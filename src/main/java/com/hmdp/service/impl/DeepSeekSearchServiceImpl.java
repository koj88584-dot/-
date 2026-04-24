package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.AmapSearchResultDTO;
import com.hmdp.dto.CityProfileDTO;
import com.hmdp.dto.DeepSeekSearchDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.SearchResultDTO;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.BrowseHistory;
import com.hmdp.entity.Favorites;
import com.hmdp.entity.Shop;
import com.hmdp.service.IAmapService;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IBrowseHistoryService;
import com.hmdp.service.ICityService;
import com.hmdp.service.IDeepSeekSearchService;
import com.hmdp.service.IFavoritesService;
import com.hmdp.service.IShopService;
import com.hmdp.service.IShopSyncService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * DeepSeek 智能搜索服务实现
 */
@Slf4j
@Service
public class DeepSeekSearchServiceImpl implements IDeepSeekSearchService {

    private static final String HOT_SEARCH_KEY = "search:hot";
    private static final String SEARCH_HISTORY_KEY = "search:history:";
    private static final String SEARCH_SUGGESTIONS_KEY = "search:suggestions";
    private static final int SEARCH_RADIUS_METERS = 10000;
    private static final int LOCAL_SEARCH_LIMIT = 120;
    private static final int MIN_USEFUL_RESULTS = 3;

    @Resource
    private IShopService shopService;

    @Resource
    private IBlogService blogService;

    @Resource
    private IFavoritesService favoritesService;

    @Resource
    private IBrowseHistoryService browseHistoryService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IAmapService amapService;

    @Resource
    private IShopSyncService shopSyncService;

    @Resource
    private ICityService cityService;

    @Override
    public Result search(DeepSeekSearchDTO searchDTO) {
        long startTime = System.currentTimeMillis();

        if (searchDTO == null || StrUtil.isBlank(searchDTO.getKeyword())) {
            return Result.fail("搜索关键词不能为空");
        }
        String keyword = searchDTO.getKeyword();

        SearchQueryContext context = resolveSearchContext(searchDTO);
        CityProfileDTO cityProfile = StrUtil.isBlank(searchDTO.getCityCode())
                ? null
                : cityService.matchCityProfile(searchDTO.getCityCode(), null);
        String type = StrUtil.blankToDefault(searchDTO.getType(), "all");
        int current = searchDTO.getCurrent() == null || searchDTO.getCurrent() < 1 ? 1 : searchDTO.getCurrent();

        stringRedisTemplate.opsForZSet().incrementScore(HOT_SEARCH_KEY, keyword.trim(), 1);

        SearchResultDTO result = new SearchResultDTO();
        SearchSlice<Shop> shopSlice = SearchSlice.empty(current);
        SearchSlice<Blog> blogSlice = SearchSlice.empty(current);

        if ("shop".equals(type) || "all".equals(type)) {
            shopSlice = searchShops(searchDTO, context, cityProfile, current);
        }

        if ("blog".equals(type) || "all".equals(type)) {
            blogSlice = searchBlogs(context, current);
        }

        long currentCount = shopSlice.currentCount + blogSlice.currentCount;
        result.setShops(shopSlice.records);
        result.setBlogs(blogSlice.records);
        result.setTotal(currentCount);
        result.setCurrentCount(currentCount);
        result.setTotalHits(shopSlice.totalHits + blogSlice.totalHits);
        result.setHasMore(shopSlice.hasMore || blogSlice.hasMore);
        result.setCurrentPage(current);
        result.setPageSize(SystemConstants.MAX_PAGE_SIZE);
        result.setCostTime(System.currentTimeMillis() - startTime);
        result.setSuggestions(generateSuggestions(context.originalKeyword));

        return Result.ok(result);
    }

    private SearchSlice<Shop> searchShops(DeepSeekSearchDTO dto, SearchQueryContext context, CityProfileDTO cityProfile, int current) {
        int pageSize = SystemConstants.MAX_PAGE_SIZE;
        int probePage = current + 1;
        long localTotalHits = countLocalShopCandidates(dto, context, cityProfile);
        List<Shop> localCandidates = queryLocalShopCandidates(dto, context, cityProfile, probePage);

        // Progressive widening: if strict query returns too few, relax typeId then city
        if (localCandidates.size() < MIN_USEFUL_RESULTS && context.resolvedTypeId != null) {
            log.info("Search widening: dropping typeId filter for keyword={}", context.originalKeyword);
            SearchQueryContext relaxedContext = new SearchQueryContext(
                    context.originalKeyword, context.keywords, null, context.amapKeyword);
            List<Shop> wider = queryLocalShopCandidates(dto, relaxedContext, cityProfile, probePage);
            mergeUniqueShops(localCandidates, wider);
            localTotalHits = Math.max(localTotalHits, localCandidates.size());
        }
        if (localCandidates.size() < MIN_USEFUL_RESULTS && cityProfile != null && StrUtil.isNotBlank(cityProfile.getCityCode())) {
            log.info("Search widening: dropping city filter for keyword={}", context.originalKeyword);
            SearchQueryContext relaxedContext = new SearchQueryContext(
                    context.originalKeyword, context.keywords, null, context.amapKeyword);
            List<Shop> wider = queryLocalShopCandidates(dto, relaxedContext, null, probePage);
            mergeUniqueShops(localCandidates, wider);
            localTotalHits = Math.max(localTotalHits, localCandidates.size());
        }
        // Type-only fallback: if keyword returned nothing, try by type alone
        if (localCandidates.size() < MIN_USEFUL_RESULTS) {
            Long fallbackTypeId = resolveFallbackTypeId(dto, context);
            if (fallbackTypeId != null) {
                log.info("Search widening: type-only fallback typeId={} for keyword={}", fallbackTypeId, context.originalKeyword);
                List<Shop> typeFallback = queryTypeFallbackCandidates(dto, fallbackTypeId, probePage);
                mergeUniqueShops(localCandidates, typeFallback);
                localTotalHits = Math.max(localTotalHits, localCandidates.size());
            }
        }

        AmapSearchResultDTO amapWindow = queryAmapShopCandidates(dto, context, current);
        if (Boolean.TRUE.equals(amapWindow.getSuccess())) {
            List<Shop> realtimeShops = syncRealtimeShops(amapWindow.getShops(), dto.getLongitude(), dto.getLatitude());
            mergeUniqueShops(localCandidates, realtimeShops);
            if (localCandidates.isEmpty() && cityProfile != null && StrUtil.isNotBlank(cityProfile.getCityCode())) {
                shopSyncService.batchSync(context.resolvedTypeId == null ? null : context.resolvedTypeId.intValue(), cityProfile.getCityCode(), context.amapKeyword);
                List<Shop> syncedLocal = queryLocalShopCandidates(dto, context, cityProfile, probePage);
                mergeUniqueShops(localCandidates, syncedLocal);
                localTotalHits = Math.max(localTotalHits, localCandidates.size());
            }
            sortShops(localCandidates, dto, context, cityProfile);
            decorateDecisionNarrative(localCandidates, cityProfile);
            long amapHits = amapWindow.getTotalHits() == null ? realtimeShops.size() : amapWindow.getTotalHits();
            long totalHits = Math.max(localCandidates.size(), localTotalHits + amapHits);
            boolean hasMore = Boolean.TRUE.equals(amapWindow.getHasMore())
                    || localTotalHits > (long) current * pageSize
                    || localCandidates.size() > (long) current * pageSize;
            return SearchSlice.of(paginateShops(localCandidates, current), totalHits, hasMore, current);
        }

        if (localCandidates.isEmpty() && cityProfile != null && StrUtil.isNotBlank(cityProfile.getCityCode())) {
            shopSyncService.batchSync(context.resolvedTypeId == null ? null : context.resolvedTypeId.intValue(), cityProfile.getCityCode(), context.amapKeyword);
            localCandidates = queryLocalShopCandidates(dto, context, cityProfile, probePage);
            localTotalHits = Math.max(localTotalHits, localCandidates.size());
        }
        sortShops(localCandidates, dto, context, cityProfile);
        decorateDecisionNarrative(localCandidates, cityProfile);
        boolean hasMore = localTotalHits > (long) current * pageSize;
        return SearchSlice.of(paginateShops(localCandidates, current), localTotalHits, hasMore, current);
    }

    private long countLocalShopCandidates(DeepSeekSearchDTO dto, SearchQueryContext context, CityProfileDTO cityProfile) {
        return shopService.count(buildLocalShopQueryWrapper(dto, context, cityProfile));
    }

    private List<Shop> queryLocalShopCandidates(DeepSeekSearchDTO dto, SearchQueryContext context, CityProfileDTO cityProfile, int probePage) {
        int requiredSize = Math.min(
                Math.max(probePage * SystemConstants.MAX_PAGE_SIZE * 4, SystemConstants.MAX_PAGE_SIZE * 2),
                LOCAL_SEARCH_LIMIT
        );

        LambdaQueryWrapper<Shop> wrapper = buildLocalShopQueryWrapper(dto, context, cityProfile);
        wrapper.orderByDesc(Shop::getSold)
                .orderByDesc(Shop::getScore)
                .last("limit " + requiredSize);

        List<Shop> shops = shopService.list(wrapper);
        normalizeShops(shops);
        enrichDistances(shops, dto.getLongitude(), dto.getLatitude());
        return shops;
    }

    private long countTypeFallbackCandidates(DeepSeekSearchDTO dto, Long typeId) {
        return shopService.count(buildTypeFallbackQueryWrapper(dto, typeId));
    }

    private List<Shop> queryTypeFallbackCandidates(DeepSeekSearchDTO dto, Long typeId, int probePage) {
        int requiredSize = Math.min(
                Math.max(probePage * SystemConstants.MAX_PAGE_SIZE * 3, SystemConstants.MAX_PAGE_SIZE * 2),
                LOCAL_SEARCH_LIMIT
        );
        LambdaQueryWrapper<Shop> wrapper = buildTypeFallbackQueryWrapper(dto, typeId);
        wrapper.orderByDesc(Shop::getScore)
                .orderByDesc(Shop::getSold)
                .last("limit " + requiredSize);
        List<Shop> shops = shopService.list(wrapper);
        normalizeShops(shops);
        enrichDistances(shops, dto.getLongitude(), dto.getLatitude());
        return shops;
    }

    private LambdaQueryWrapper<Shop> buildLocalShopQueryWrapper(DeepSeekSearchDTO dto, SearchQueryContext context, CityProfileDTO cityProfile) {
        LambdaQueryWrapper<Shop> wrapper = new LambdaQueryWrapper<>();
        appendShopKeywordConditions(wrapper, context.keywords);
        if (context.resolvedTypeId != null) {
            wrapper.eq(Shop::getTypeId, context.resolvedTypeId);
        }
        applyCityHint(wrapper, cityProfile);
        if (dto.getMinPrice() != null) {
            wrapper.ge(Shop::getAvgPrice, dto.getMinPrice());
        }
        if (dto.getMaxPrice() != null) {
            wrapper.le(Shop::getAvgPrice, dto.getMaxPrice());
        }
        return wrapper;
    }

    private LambdaQueryWrapper<Shop> buildTypeFallbackQueryWrapper(DeepSeekSearchDTO dto, Long typeId) {
        LambdaQueryWrapper<Shop> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(typeId != null, Shop::getTypeId, typeId);
        if (dto.getMinPrice() != null) {
            wrapper.ge(Shop::getAvgPrice, dto.getMinPrice());
        }
        if (dto.getMaxPrice() != null) {
            wrapper.le(Shop::getAvgPrice, dto.getMaxPrice());
        }
        return wrapper;
    }

    private AmapSearchResultDTO queryAmapShopCandidates(DeepSeekSearchDTO dto, SearchQueryContext context, int current) {
        Integer typeId = context.resolvedTypeId == null ? null : context.resolvedTypeId.intValue();
        AmapSearchResultDTO result = amapService.searchTextShopsWithMeta(
                typeId,
                context.amapKeyword,
                dto.getLongitude(),
                dto.getLatitude(),
                current,
                SystemConstants.MAX_PAGE_SIZE
        );
        if (result == null) {
            return AmapSearchResultDTO.empty(current, SystemConstants.MAX_PAGE_SIZE);
        }
        normalizeShops(result.getShops());
        enrichDistances(result.getShops(), dto.getLongitude(), dto.getLatitude());
        return result;
    }

    private List<Shop> syncRealtimeShops(List<Shop> amapShops, Double longitude, Double latitude) {
        if (amapShops == null || amapShops.isEmpty()) {
            return Collections.emptyList();
        }
        List<Shop> synced = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Shop amapShop : amapShops) {
            if (amapShop == null) {
                continue;
            }
            try {
                Shop localShop = shopSyncService.getOrSync(amapShop);
                Shop candidate = localShop != null ? localShop : amapShop;
                normalizeShops(Collections.singletonList(candidate));
                enrichDistances(Collections.singletonList(candidate), longitude, latitude);
                if (candidate.getId() != null && seen.add(candidate.getId())) {
                    synced.add(candidate);
                }
            } catch (Exception e) {
                log.warn("Sync realtime search shop failed, shopId={}", amapShop.getId(), e);
                normalizeShops(Collections.singletonList(amapShop));
                enrichDistances(Collections.singletonList(amapShop), longitude, latitude);
                if (amapShop.getId() != null && seen.add(amapShop.getId())) {
                    synced.add(amapShop);
                }
            }
        }
        return synced;
    }

    private boolean shouldUseAmapFallback(DeepSeekSearchDTO dto, long localTotalHits, int current) {
        if (dto.getLongitude() == null || dto.getLatitude() == null) {
            return false;
        }
        return localTotalHits < (long) current * SystemConstants.MAX_PAGE_SIZE;
    }

    private void appendShopKeywordConditions(LambdaQueryWrapper<Shop> wrapper, List<String> keywords) {
        wrapper.and(group -> {
            boolean appended = false;
            for (String keyword : keywords) {
                String term = StrUtil.trim(keyword);
                if (StrUtil.isBlank(term)) {
                    continue;
                }
                if (appended) {
                    group.or();
                }
                group.and(inner -> inner.like(Shop::getName, term)
                        .or()
                        .like(Shop::getAddress, term)
                        .or()
                        .like(Shop::getArea, term));
                appended = true;
            }
        });
    }

    private SearchSlice<Blog> searchBlogs(SearchQueryContext context, int current) {
        LambdaQueryWrapper<Blog> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(group -> {
            boolean appended = false;
            for (String keyword : context.keywords) {
                String term = StrUtil.trim(keyword);
                if (StrUtil.isBlank(term)) {
                    continue;
                }
                if (appended) {
                    group.or();
                }
                group.and(inner -> inner.like(Blog::getTitle, term)
                        .or()
                        .like(Blog::getContent, term));
                appended = true;
            }
        }).orderByDesc(Blog::getLiked)
                .orderByDesc(Blog::getCreateTime);

        Page<Blog> page = blogService.page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE), wrapper);
        boolean hasMore = (long) current * SystemConstants.MAX_PAGE_SIZE < page.getTotal();
        return SearchSlice.of(page.getRecords(), page.getTotal(), hasMore, current);
    }

    private SearchQueryContext resolveSearchContext(DeepSeekSearchDTO dto) {
        return SearchKeywordSupport.resolveSearchContext(dto);
    }

    private Long resolveFallbackTypeId(DeepSeekSearchDTO dto, SearchQueryContext context) {
        if (context.resolvedTypeId != null) {
            return context.resolvedTypeId;
        }
        String keyword = StrUtil.blankToDefault(dto.getKeyword(), "").toLowerCase();
        if (containsAny(keyword,
                "\u5976\u8336", "\u8336\u996e", "\u996e\u54c1", "\u5496\u5561", "\u4e0b\u5348\u8336",
                "\u706b\u9505", "\u70e7\u70e4", "\u70e4\u8089", "\u751c\u54c1", "\u9762\u5305",
                "\u7f8e\u98df", "\u9910\u5385", "\u996d\u5e97", "\u5c0f\u5403", "\u5bff\u53f8",
                "\u897f\u9910", "\u725b\u6392", "\u6d77\u9c9c")) {
            return 1L;
        }
        if (containsAny(keyword, "ktv", "\u5531\u6b4c", "\u9ea6\u9738")) {
            return 2L;
        }
        if (containsAny(keyword, "\u7f8e\u53d1", "\u4e3d\u4eba", "\u7406\u53d1")) {
            return 3L;
        }
        if (containsAny(keyword, "\u5065\u8eab", "\u8fd0\u52a8", "\u745c\u4f3d")) {
            return 4L;
        }
        if (containsAny(keyword, "\u6309\u6469", "\u8db3\u7597", "\u63a8\u62ff")) {
            return 5L;
        }
        if (containsAny(keyword, "spa", "\u7f8e\u5bb9", "\u62a4\u7406")) {
            return 6L;
        }
        if (containsAny(keyword, "\u4eb2\u5b50", "\u905b\u5a03", "\u513f\u7ae5")) {
            return 7L;
        }
        if (containsAny(keyword, "\u9152\u5427", "\u6e05\u5427", "\u5c0f\u9152\u9986")) {
            return 8L;
        }
        if (containsAny(keyword, "\u8f70\u8db4", "\u805a\u4f1a", "\u6d3e\u5bf9")) {
            return 9L;
        }
        if (containsAny(keyword, "\u7f8e\u7532", "\u7f8e\u776b", "\u776b\u6bdb")) {
            return 10L;
        }
        return null;
    }

    private boolean containsAny(String text, String... tokens) {
        if (StrUtil.isBlank(text) || tokens == null) {
            return false;
        }
        for (String token : tokens) {
            if (StrUtil.isNotBlank(token) && text.contains(token.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private void sortShops(List<Shop> shops, DeepSeekSearchDTO dto, SearchQueryContext context, CityProfileDTO cityProfile) {
        String sortBy = StrUtil.blankToDefault(dto.getSortBy(), "relevance");
        switch (sortBy) {
            case "distance":
                shops.sort(Comparator.comparing(shop -> shop.getDistance() == null ? Double.MAX_VALUE : shop.getDistance()));
                break;
            case "rating":
                shops.sort(Comparator
                        .comparing((Shop shop) -> shop.getScore() == null ? 0 : shop.getScore()).reversed()
                        .thenComparing(shop -> shop.getComments() == null ? 0 : shop.getComments(), Comparator.reverseOrder()));
                break;
            case "price":
                shops.sort(Comparator
                        .comparing((Shop shop) -> shop.getAvgPrice() == null ? Long.MAX_VALUE : shop.getAvgPrice())
                        .thenComparing(shop -> shop.getScore() == null ? 0 : shop.getScore(), Comparator.reverseOrder()));
                break;
            case "relevance":
            default:
                shops.sort(Comparator
                        .comparing((Shop shop) -> calculateRelevanceScore(shop, context, cityProfile)).reversed()
                        .thenComparing(shop -> shop.getDistance() == null ? Double.MAX_VALUE : shop.getDistance()));
                break;
        }
    }

    private double calculateRelevanceScore(Shop shop, SearchQueryContext context, CityProfileDTO cityProfile) {
        double score = 0;
        String shopName = StrUtil.blankToDefault(shop.getName(), "");
        String address = StrUtil.blankToDefault(shop.getAddress(), "");
        String area = StrUtil.blankToDefault(shop.getArea(), "");

        for (String keyword : context.keywords) {
            if (StrUtil.isBlank(keyword)) {
                continue;
            }
            if (StrUtil.containsIgnoreCase(shopName, keyword)) {
                score += 36;
                if (shopName.startsWith(keyword)) {
                    score += 12;
                }
            }
            if (StrUtil.containsIgnoreCase(address, keyword)) {
                score += 12;
            }
            if (StrUtil.containsIgnoreCase(area, keyword)) {
                score += 8;
            }
        }

        if (context.resolvedTypeId != null && Objects.equals(shop.getTypeId(), context.resolvedTypeId)) {
            score += 10;
        }
        if (shop.getScore() != null) {
            score += (shop.getScore() / 50.0) * 22;
        }
        if (shop.getSold() != null) {
            score += Math.min(shop.getSold() / 1000.0, 1.0) * 16;
        }
        if (shop.getComments() != null) {
            score += Math.min(shop.getComments() / 500.0, 1.0) * 10;
        }
        if (shop.getDistance() != null) {
            score += Math.max(0, 12 - (shop.getDistance() / 1000.0));
        }
        if (cityProfile != null) {
            String district = StrUtil.blankToDefault(shop.getDistrict(), "");
            String cityName = StrUtil.blankToDefault(shop.getCity(), "");
            if (cityProfile.getCityName().equals(cityName) || address.contains(cityProfile.getCityName())) {
                score += 14;
            }
            for (String business : cityProfile.getFeaturedDistricts()) {
                if (StrUtil.isBlank(business)) {
                    continue;
                }
                if (area.contains(business) || district.contains(business) || address.contains(business)) {
                    score += 9;
                }
            }
            for (String category : cityProfile.getPrimaryCategories()) {
                if (StrUtil.isBlank(category)) {
                    continue;
                }
                if (shopName.contains(category) || address.contains(category)) {
                    score += 6;
                }
            }
        }
        return score;
    }

    private List<Shop> paginateShops(List<Shop> shops, int current) {
        int from = (current - 1) * SystemConstants.MAX_PAGE_SIZE;
        if (from >= shops.size()) {
            return Collections.emptyList();
        }
        int to = Math.min(from + SystemConstants.MAX_PAGE_SIZE, shops.size());
        return new ArrayList<>(shops.subList(from, to));
    }

    private void mergeUniqueShops(List<Shop> target, List<Shop> source) {
        LinkedHashMap<String, Shop> merged = new LinkedHashMap<>();
        for (Shop shop : target) {
            merged.put(buildUniqueShopKey(shop), shop);
        }
        for (Shop shop : source) {
            merged.putIfAbsent(buildUniqueShopKey(shop), shop);
        }
        target.clear();
        target.addAll(merged.values());
    }

    private String buildUniqueShopKey(Shop shop) {
        String name = StrUtil.blankToDefault(shop.getName(), "").trim().toLowerCase();
        String address = StrUtil.blankToDefault(shop.getAddress(), "").trim().toLowerCase();
        if (StrUtil.isNotBlank(name) || StrUtil.isNotBlank(address)) {
            return name + "|" + address;
        }
        return String.valueOf(shop.getId());
    }

    private void normalizeShops(List<Shop> shops) {
        if (shops == null) {
            return;
        }
        for (Shop shop : shops) {
            if (shop == null) {
                continue;
            }
            if (StrUtil.isBlank(shop.getImages())) {
                shop.setImages("/imgs/shop/default.png");
            }
            if (shop.getComments() == null) {
                shop.setComments(0);
            }
            if (shop.getScore() == null) {
                shop.setScore(0);
            }
        }
    }

    private void enrichDistances(List<Shop> shops, Double longitude, Double latitude) {
        if (shops == null || longitude == null || latitude == null) {
            return;
        }
        for (Shop shop : shops) {
            if (shop == null || shop.getX() == null || shop.getY() == null) {
                continue;
            }
            shop.setDistance(calculateDistance(longitude, latitude, shop.getX(), shop.getY()));
        }
    }

    private void applyCityHint(LambdaQueryWrapper<Shop> wrapper, CityProfileDTO cityProfile) {
        if (wrapper == null || cityProfile == null || StrUtil.isBlank(cityProfile.getCityCode())) {
            return;
        }
        String prefix = cityProfile.getCityCode().length() >= 4
                ? cityProfile.getCityCode().substring(0, 4)
                : cityProfile.getCityCode();
        wrapper.and(group -> group.eq(Shop::getCityCode, cityProfile.getCityCode())
                .or()
                .likeRight(Shop::getAdcode, prefix)
                .or()
                .like(Shop::getCity, cityProfile.getCityName())
                .or()
                .like(Shop::getAddress, cityProfile.getCityName())
                .or()
                .isNull(Shop::getCityCode));
    }

    private void decorateDecisionNarrative(List<Shop> shops, CityProfileDTO cityProfile) {
        if (shops == null || shops.isEmpty() || cityProfile == null) {
            return;
        }
        List<String> defaultScenes = cityProfile.getDefaultScenes() == null
                ? Collections.emptyList()
                : cityProfile.getDefaultScenes();
        for (Shop shop : shops) {
            if (shop == null) {
                continue;
            }
            List<String> sceneTags = new ArrayList<>();
            String area = StrUtil.blankToDefault(shop.getArea(), "");
            for (String district : cityProfile.getFeaturedDistricts()) {
                if (StrUtil.isNotBlank(district) && area.contains(district)) {
                    sceneTags.add(district);
                }
            }
            for (String scene : defaultScenes) {
                if (sceneTags.size() >= 3) {
                    break;
                }
                sceneTags.add(scene);
            }
            shop.setSceneTags(sceneTags);
            StringBuilder reason = new StringBuilder("更适合在");
            reason.append(cityProfile.getCityName()).append("做这类消费决策");
            if (StrUtil.isNotBlank(area)) {
                reason.append("，").append(area).append("更容易接上这座城的节奏");
            } else if (!defaultScenes.isEmpty()) {
                reason.append("，尤其适合").append(defaultScenes.get(0));
            }
            shop.setDecisionReason(reason.toString());
        }
    }

    private double calculateDistance(double lng1, double lat1, double lng2, double lat2) {
        double radLat1 = Math.toRadians(lat1);
        double radLat2 = Math.toRadians(lat2);
        double a = radLat1 - radLat2;
        double b = Math.toRadians(lng1) - Math.toRadians(lng2);
        double s = 2 * Math.asin(Math.sqrt(Math.pow(Math.sin(a / 2), 2)
                + Math.cos(radLat1) * Math.cos(radLat2) * Math.pow(Math.sin(b / 2), 2)));
        return s * 6378137;
    }


    private List<String> generateSuggestions(String keyword) {
        Set<String> suggestions = stringRedisTemplate.opsForZSet()
                .reverseRange(SEARCH_SUGGESTIONS_KEY, 0, 9);

        if (suggestions == null) {
            return Collections.emptyList();
        }

        return suggestions.stream()
                .filter(s -> StrUtil.containsIgnoreCase(s, keyword) || StrUtil.containsIgnoreCase(keyword, s))
                .limit(5)
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public Result getSuggestions(String prefix) {
        if (StrUtil.isBlank(prefix)) {
            return Result.ok(Collections.emptyList());
        }

        Set<String> hotSearches = stringRedisTemplate.opsForZSet()
                .reverseRange(HOT_SEARCH_KEY, 0, 99);

        if (hotSearches == null) {
            return Result.ok(Collections.emptyList());
        }

        List<String> suggestions = hotSearches.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
                .limit(10)
                .collect(java.util.stream.Collectors.toList());

        return Result.ok(suggestions);
    }

    @Override
    public Result getHotSearch() {
        Set<ZSetOperations.TypedTuple<String>> hotSearches = stringRedisTemplate.opsForZSet()
                .reverseRangeWithScores(HOT_SEARCH_KEY, 0, 19);

        if (hotSearches == null) {
            return Result.ok(Collections.emptyList());
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (ZSetOperations.TypedTuple<String> tuple : hotSearches) {
            String value = tuple.getValue();
            // Skip garbage entries: JSON objects, too long strings, or blank values
            if (StrUtil.isBlank(value) || value.length() > 20
                    || value.startsWith("{") || value.startsWith("[")
                    || value.contains("\"id\"") || value.contains("\"title\"")) {
                continue;
            }
            if (result.size() >= 10) {
                break;
            }
            Map<String, Object> item = new HashMap<>();
            item.put("keyword", value);
            item.put("count", tuple.getScore().longValue());
            result.add(item);
        }

        return Result.ok(result);
    }

    @Override
    public Result getRecommendations(Integer current) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return getHotShops(current);
        }

        Long userId = user.getId();
        int pageNo = current == null || current < 1 ? 1 : current;
        Set<Long> viewedShopIds = new HashSet<>();
        Set<Long> viewedTypeIds = new HashSet<>();
        Set<Long> referencedShopIds = new HashSet<>();

        List<BrowseHistory> histories = browseHistoryService.lambdaQuery()
                .eq(BrowseHistory::getUserId, userId)
                .eq(BrowseHistory::getType, 1)
                .orderByDesc(BrowseHistory::getBrowseTime)
                .last("limit 50")
                .list();

        collectTargetIds(histories, BrowseHistory::getTargetId, viewedShopIds::add, referencedShopIds::add);

        List<Favorites> favorites = favoritesService.lambdaQuery()
                .eq(Favorites::getUserId, userId)
                .eq(Favorites::getType, 1)
                .list();

        collectTargetIds(favorites, Favorites::getTargetId, viewedShopIds::add, referencedShopIds::add);
        Map<Long, Shop> viewedShopMap = loadShopLookup(referencedShopIds);
        for (Long shopId : viewedShopIds) {
            Shop shop = viewedShopMap.get(shopId);
            if (shop != null && shop.getTypeId() != null) {
                viewedTypeIds.add(shop.getTypeId());
            }
        }

        Page<Shop> page;
        if (!viewedTypeIds.isEmpty()) {
            page = shopService.lambdaQuery()
                    .in(Shop::getTypeId, viewedTypeIds)
                    .notIn(!viewedShopIds.isEmpty(), Shop::getId, viewedShopIds)
                    .orderByDesc(Shop::getScore)
                    .page(new Page<>(pageNo, SystemConstants.MAX_PAGE_SIZE));
        } else {
            return getHotShops(pageNo);
        }

        return Result.ok(page.getRecords());
    }

    private Result getHotShops(Integer current) {
        int pageNo = current == null || current < 1 ? 1 : current;
        Page<Shop> page = shopService.lambdaQuery()
                .orderByDesc(Shop::getSold)
                .page(new Page<>(pageNo, SystemConstants.MAX_PAGE_SIZE));
        return Result.ok(page.getRecords());
    }

    @SafeVarargs
    private final <T> void collectTargetIds(List<T> records,
                                            Function<T, Long> idExtractor,
                                            Consumer<Long>... collectors) {
        if (records == null || records.isEmpty()) {
            return;
        }
        for (T record : records) {
            if (record == null) {
                continue;
            }
            Long targetId = idExtractor.apply(record);
            if (targetId == null) {
                continue;
            }
            for (Consumer<Long> collector : collectors) {
                if (collector != null) {
                    collector.accept(targetId);
                }
            }
        }
    }

    private Map<Long, Shop> loadShopLookup(Set<Long> shopIds) {
        if (shopIds == null || shopIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return shopService.listByIds(shopIds).stream()
                .filter(Objects::nonNull)
                .filter(shop -> shop.getId() != null)
                .collect(Collectors.toMap(Shop::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
    }

    @Override
    public Result recordSearchHistory(String keyword) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.ok();
        }

        String key = SEARCH_HISTORY_KEY + user.getId();
        stringRedisTemplate.opsForZSet().remove(key, keyword);
        stringRedisTemplate.opsForZSet().add(key, keyword, System.currentTimeMillis());
        stringRedisTemplate.opsForZSet().removeRange(key, 0, -51);
        stringRedisTemplate.expire(key, 30, TimeUnit.DAYS);
        return Result.ok();
    }

    @Override
    public Result getSearchHistory() {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.ok(Collections.emptyList());
        }

        String key = SEARCH_HISTORY_KEY + user.getId();
        Set<String> histories = stringRedisTemplate.opsForZSet().reverseRange(key, 0, 19);
        return Result.ok(histories != null ? histories : Collections.emptyList());
    }

    @Override
    public Result clearSearchHistory() {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.ok();
        }

        stringRedisTemplate.delete(SEARCH_HISTORY_KEY + user.getId());
        return Result.ok();
    }


    private static final class SearchSlice<T> {
        private final List<T> records;
        private final long totalHits;
        private final boolean hasMore;
        private final long currentCount;
        private final int currentPage;

        private SearchSlice(List<T> records, long totalHits, boolean hasMore, int currentPage) {
            this.records = records == null ? Collections.emptyList() : records;
            this.totalHits = totalHits;
            this.hasMore = hasMore;
            this.currentCount = this.records.size();
            this.currentPage = currentPage;
        }

        private static <T> SearchSlice<T> of(List<T> records, long totalHits, boolean hasMore, int currentPage) {
            return new SearchSlice<>(records, totalHits, hasMore, currentPage);
        }

        private static <T> SearchSlice<T> empty(int currentPage) {
            return new SearchSlice<>(Collections.emptyList(), 0L, false, currentPage);
        }
    }
}
