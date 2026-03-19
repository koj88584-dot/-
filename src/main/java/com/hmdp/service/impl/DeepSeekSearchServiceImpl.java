package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.DeepSeekSearchDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.SearchResultDTO;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.BrowseHistory;
import com.hmdp.entity.Favorites;
import com.hmdp.entity.Shop;
import com.hmdp.service.*;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.var;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

/**
 * DeepSeek智能搜索服务实现
 */
@Service
public class DeepSeekSearchServiceImpl implements IDeepSeekSearchService {

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

    private static final String HOT_SEARCH_KEY = "search:hot";
    private static final String SEARCH_HISTORY_KEY = "search:history:";
    private static final String SEARCH_SUGGESTIONS_KEY = "search:suggestions";

    @Override
    public Result search(DeepSeekSearchDTO searchDTO) {
        long startTime = System.currentTimeMillis();
        
        String keyword = searchDTO.getKeyword();
        if (StrUtil.isBlank(keyword)) {
            return Result.fail("搜索关键词不能为空");
        }
        
        SearchResultDTO result = new SearchResultDTO();
        List<Shop> shopList = new ArrayList<>();
        List<Blog> blogList = new ArrayList<>();
        
        // 记录热门搜索
        stringRedisTemplate.opsForZSet().incrementScore(HOT_SEARCH_KEY, keyword, 1);
        
        // 根据类型搜索
        String type = searchDTO.getType();
        int current = searchDTO.getCurrent() != null ? searchDTO.getCurrent() : 1;
        
        if ("shop".equals(type) || "all".equals(type)) {
            shopList = searchShops(searchDTO, current);
        }
        
        if ("blog".equals(type) || "all".equals(type)) {
            blogList = searchBlogs(keyword, current);
        }
        
        // 计算相关度排序权重
        if ("relevance".equals(searchDTO.getSortBy())) {
            shopList = sortByRelevance(shopList, keyword);
        }
        
        result.setShops(shopList);
        result.setBlogs(blogList);
        result.setTotal((long) (shopList.size() + blogList.size()));
        result.setCostTime(System.currentTimeMillis() - startTime);
        
        // 生成搜索建议
        result.setSuggestions(generateSuggestions(keyword));
        
        return Result.ok(result);
    }

    /**
     * 搜索店铺
     */
    private List<Shop> searchShops(DeepSeekSearchDTO dto, int current) {
        String keyword = dto.getKeyword();
        
        // 构建查询条件
        var queryWrapper = shopService.lambdaQuery()
                .like(Shop::getName, keyword)
                .or()
                .like(Shop::getAddress, keyword);
        
        // 类型筛选
        if (dto.getTypeId() != null) {
            queryWrapper.eq(Shop::getTypeId, dto.getTypeId());
        }
        
        // 价格筛选
        if (dto.getMinPrice() != null) {
            queryWrapper.ge(Shop::getAvgPrice, dto.getMinPrice());
        }
        if (dto.getMaxPrice() != null) {
            queryWrapper.le(Shop::getAvgPrice, dto.getMaxPrice());
        }
        
        // 排序
        String sortBy = dto.getSortBy();
        if ("rating".equals(sortBy)) {
            queryWrapper.orderByDesc(Shop::getScore);
        } else if ("price".equals(sortBy)) {
            queryWrapper.orderByAsc(Shop::getAvgPrice);
        } else if ("distance".equals(sortBy) && dto.getLongitude() != null && dto.getLatitude() != null) {
            // 距离排序使用GEO
            return searchShopsByDistance(dto, current);
        } else {
            // 默认按销量排序
            queryWrapper.orderByDesc(Shop::getSold);
        }
        
        Page<Shop> page = queryWrapper.page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        return page.getRecords();
    }

    /**
     * 按距离搜索店铺 - 使用 Redis GEO
     */
    private List<Shop> searchShopsByDistance(DeepSeekSearchDTO dto, int current) {
        Double x = dto.getLongitude();
        Double y = dto.getLatitude();
        String keyword = dto.getKeyword();
        
        // 从 Redis GEO 查询附近的所有店铺（不分类型）
        List<ShopDistanceDTO> nearbyShops = queryNearbyShopsFromGeo(x, y, 10000); // 10km范围
        
        if (nearbyShops.isEmpty()) {
            return Collections.emptyList();
        }
        
        // 获取店铺ID列表
        List<Long> shopIds = nearbyShops.stream()
                .map(ShopDistanceDTO::getShopId)
                .collect(Collectors.toList());
        
        // 从数据库查询这些店铺的详细信息，并筛选匹配关键词的
        List<Shop> shops = shopService.lambdaQuery()
                .in(Shop::getId, shopIds)
                .and(wrapper -> wrapper
                        .like(Shop::getName, keyword)
                        .or()
                        .like(Shop::getAddress, keyword)
                )
                .list();
        
        if (shops.isEmpty()) {
            return Collections.emptyList();
        }
        
        // 构建距离映射
        Map<Long, Double> distanceMap = nearbyShops.stream()
                .collect(Collectors.toMap(
                        ShopDistanceDTO::getShopId,
                        ShopDistanceDTO::getDistance
                ));
        
        // 设置距离并排序
        shops.forEach(shop -> {
            Double distance = distanceMap.get(shop.getId());
            shop.setDistance(distance != null ? distance : Double.MAX_VALUE);
        });
        
        // 按距离升序排序
        shops.sort(Comparator.comparing(Shop::getDistance));
        
        // 分页
        int from = (current - 1) * SystemConstants.MAX_PAGE_SIZE;
        int to = Math.min(from + SystemConstants.MAX_PAGE_SIZE, shops.size());
        
        if (from >= shops.size()) {
            return Collections.emptyList();
        }
        
        return shops.subList(from, to);
    }
    
    /**
     * 从 Redis GEO 查询附近店铺
     * 遍历所有类型的 GEO key
     */
    private List<ShopDistanceDTO> queryNearbyShopsFromGeo(Double x, Double y, double radiusMeters) {
        List<ShopDistanceDTO> result = new ArrayList<>();
        
        // 获取所有店铺类型ID（从1到10，或者从Redis中动态获取）
        Set<String> keys = stringRedisTemplate.keys(SHOP_GEO_KEY + "*");
        System.out.println("[DEBUG] Redis GEO keys found: " + keys);
        
        if (keys == null || keys.isEmpty()) {
            System.out.println("[DEBUG] No GEO keys found in Redis");
            return result;
        }
        
        for (String key : keys) {
            try {
                System.out.println("[DEBUG] Querying GEO key: " + key);
                GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                        .search(key,
                                GeoReference.fromCoordinate(x, y),
                                new Distance(radiusMeters, RedisGeoCommands.DistanceUnit.METERS),
                                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs()
                                        .includeDistance()
                                        .sortAscending()
                        );
                
                System.out.println("[DEBUG] Results for key " + key + ": " + (results != null ? results.getContent().size() : "null"));
                
                if (results != null) {
                    for (GeoResult<RedisGeoCommands.GeoLocation<String>> geoResult : results.getContent()) {
                        String shopIdStr = geoResult.getContent().getName();
                        Distance distance = geoResult.getDistance();
                        
                        try {
                            Long shopId = Long.valueOf(shopIdStr);
                            result.add(new ShopDistanceDTO(shopId, distance.getValue()));
                            System.out.println("[DEBUG] Found shop: " + shopId + " at distance: " + distance.getValue());
                        } catch (NumberFormatException e) {
                            // 忽略无效的店铺ID
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("[DEBUG] Error querying key " + key + ": " + e.getMessage());
                // 某个类型查询失败，继续查询其他类型
            }
        }
        
        System.out.println("[DEBUG] Total nearby shops found: " + result.size());
        
        // 按距离排序并去重（同一个店铺可能在多个类型中）
        return result.stream()
                .collect(Collectors.toMap(
                        ShopDistanceDTO::getShopId,
                        dto -> dto,
                        (dto1, dto2) -> dto1.getDistance() < dto2.getDistance() ? dto1 : dto2
                ))
                .values()
                .stream()
                .sorted(Comparator.comparing(ShopDistanceDTO::getDistance))
                .collect(Collectors.toList());
    }
    
    /**
     * 店铺距离DTO
     */
    private static class ShopDistanceDTO {
        private final Long shopId;
        private final Double distance;
        
        public ShopDistanceDTO(Long shopId, Double distance) {
            this.shopId = shopId;
            this.distance = distance;
        }
        
        public Long getShopId() {
            return shopId;
        }
        
        public Double getDistance() {
            return distance;
        }
    }

    /**
     * 搜索博客
     */
    private List<Blog> searchBlogs(String keyword, int current) {
        Page<Blog> page = blogService.lambdaQuery()
                .like(Blog::getTitle, keyword)
                .or()
                .like(Blog::getContent, keyword)
                .orderByDesc(Blog::getLiked)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        
        return page.getRecords();
    }

    /**
     * 相关度排序（基于多因素权重）
     */
    private List<Shop> sortByRelevance(List<Shop> shops, String keyword) {
        return shops.stream()
                .sorted((s1, s2) -> {
                    double score1 = calculateRelevanceScore(s1, keyword);
                    double score2 = calculateRelevanceScore(s2, keyword);
                    return Double.compare(score2, score1);
                })
                .collect(Collectors.toList());
    }

    /**
     * 计算相关度得分
     * 权重：名称匹配(40%) + 评分(30%) + 销量(20%) + 评论数(10%)
     */
    private double calculateRelevanceScore(Shop shop, String keyword) {
        double score = 0;
        
        // 名称匹配度
        if (shop.getName().contains(keyword)) {
            score += 40;
            if (shop.getName().startsWith(keyword)) {
                score += 10; // 前缀匹配额外加分
            }
        }
        
        // 评分权重 (1-50分 -> 0-30)
        if (shop.getScore() != null) {
            score += (shop.getScore() / 50.0) * 30;
        }
        
        // 销量权重 (归一化到0-20)
        if (shop.getSold() != null) {
            score += Math.min(shop.getSold() / 1000.0, 1.0) * 20;
        }
        
        // 评论数权重 (归一化到0-10)
        if (shop.getComments() != null) {
            score += Math.min(shop.getComments() / 500.0, 1.0) * 10;
        }
        
        return score;
    }

    /**
     * 生成搜索建议
     */
    private List<String> generateSuggestions(String keyword) {
        Set<String> suggestions = stringRedisTemplate.opsForZSet()
                .reverseRange(SEARCH_SUGGESTIONS_KEY, 0, 9);
        
        if (suggestions == null) {
            return Collections.emptyList();
        }
        
        return suggestions.stream()
                .filter(s -> s.contains(keyword) || keyword.contains(s))
                .limit(5)
                .collect(Collectors.toList());
    }

    @Override
    public Result getSuggestions(String prefix) {
        if (StrUtil.isBlank(prefix)) {
            return Result.ok(Collections.emptyList());
        }
        
        // 从热门搜索中获取匹配的建议
        Set<String> hotSearches = stringRedisTemplate.opsForZSet()
                .reverseRange(HOT_SEARCH_KEY, 0, 99);
        
        if (hotSearches == null) {
            return Result.ok(Collections.emptyList());
        }
        
        List<String> suggestions = hotSearches.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
                .limit(10)
                .collect(Collectors.toList());
        
        return Result.ok(suggestions);
    }

    @Override
    public Result getHotSearch() {
        Set<ZSetOperations.TypedTuple<String>> hotSearches = stringRedisTemplate.opsForZSet()
                .reverseRangeWithScores(HOT_SEARCH_KEY, 0, 9);
        
        if (hotSearches == null) {
            return Result.ok(Collections.emptyList());
        }
        
        List<Map<String, Object>> result = new ArrayList<>();
        for (ZSetOperations.TypedTuple<String> tuple : hotSearches) {
            Map<String, Object> item = new HashMap<>();
            item.put("keyword", tuple.getValue());
            item.put("count", tuple.getScore().longValue());
            result.add(item);
        }
        
        return Result.ok(result);
    }

    @Override
    public Result getRecommendations(Integer current) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            // 未登录用户返回热门店铺
            return getHotShops(current);
        }
        
        Long userId = user.getId();
        
        // 基于用户浏览历史和收藏进行推荐
        Set<Long> viewedShopIds = new HashSet<>();
        Set<Long> viewedTypeIds = new HashSet<>();
        
        // 获取浏览历史中的店铺类型
        List<BrowseHistory> histories = browseHistoryService.lambdaQuery()
                .eq(BrowseHistory::getUserId, userId)
                .eq(BrowseHistory::getType, 1) // 店铺
                .orderByDesc(BrowseHistory::getBrowseTime)
                .last("limit 50")
                .list();
        
        for (BrowseHistory history : histories) {
            viewedShopIds.add(history.getTargetId());
            Shop shop = shopService.getById(history.getTargetId());
            if (shop != null) {
                viewedTypeIds.add(shop.getTypeId());
            }
        }
        
        // 获取收藏的店铺类型
        List<Favorites> favorites = favoritesService.lambdaQuery()
                .eq(Favorites::getUserId, userId)
                .eq(Favorites::getType, 1)
                .list();
        
        for (Favorites fav : favorites) {
            viewedShopIds.add(fav.getTargetId());
            Shop shop = shopService.getById(fav.getTargetId());
            if (shop != null) {
                viewedTypeIds.add(shop.getTypeId());
            }
        }
        
        // 根据偏好类型推荐店铺
        Page<Shop> page;
        if (!viewedTypeIds.isEmpty()) {
            page = shopService.lambdaQuery()
                    .in(Shop::getTypeId, viewedTypeIds)
                    .notIn(!viewedShopIds.isEmpty(), Shop::getId, viewedShopIds)
                    .orderByDesc(Shop::getScore)
                    .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        } else {
            // 没有历史数据，返回热门店铺
            return getHotShops(current);
        }
        
        return Result.ok(page.getRecords());
    }

    /**
     * 获取热门店铺
     */
    private Result getHotShops(Integer current) {
        Page<Shop> page = shopService.lambdaQuery()
                .orderByDesc(Shop::getSold)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        
        return Result.ok(page.getRecords());
    }

    @Override
    public Result recordSearchHistory(String keyword) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.ok();
        }
        
        String key = SEARCH_HISTORY_KEY + user.getId();
        
        // 先删除已存在的相同关键词
        stringRedisTemplate.opsForZSet().remove(key, keyword);
        
        // 添加新的搜索记录
        stringRedisTemplate.opsForZSet().add(key, keyword, System.currentTimeMillis());
        
        // 只保留最近50条
        stringRedisTemplate.opsForZSet().removeRange(key, 0, -51);
        
        // 设置过期时间30天
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
        
        String key = SEARCH_HISTORY_KEY + user.getId();
        stringRedisTemplate.delete(key);
        
        return Result.ok();
    }
}
