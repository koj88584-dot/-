package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.AmapSearchResultDTO;
import com.hmdp.dto.CityProfileDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IAmapService;
import com.hmdp.service.IBrowseHistoryService;
import com.hmdp.service.ICityService;
import com.hmdp.service.IShopService;
import com.hmdp.service.IShopSyncService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

@Service
@Slf4j
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    private static final int[] NEARBY_SEARCH_RADII = {5000, 10000, 20000, 50000, 100000};

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Resource
    private IBrowseHistoryService browseHistoryService;

    @Resource
    private IAmapService amapService;

    @Resource
    private IShopSyncService shopSyncService;

    @Resource
    private ICityService cityService;

    @Override
    public Result queryById(Long id) {
        Shop shop = cacheClient.queryShopWithPassThrough(id, Shop.class, this::getById);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }

        UserDTO user = UserHolder.getUser();
        if (user != null) {
            try {
                browseHistoryService.addHistory(1, id);
            } catch (Exception e) {
                log.warn("Record browse history failed, shopId={}, userId={}", id, user.getId(), e);
            }
        }
        return Result.ok(shop);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("id不能为空");
        }
        updateById(shop);
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y, String cityCode) {
        try {
            int pageNo = current == null || current < 1 ? 1 : current;
            if (x == null || y == null) {
                LambdaQueryWrapper<Shop> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(Shop::getTypeId, typeId);
                applyCityHint(wrapper, cityCode);
                Page<Shop> page = page(new Page<>(pageNo, SystemConstants.MAX_PAGE_SIZE), wrapper);
                page.getRecords().forEach(ShopNearbySupport::fillDefaults);
                return Result.ok(page.getRecords());
            }

            ShopNearbyQuerySupport.NearbyQuerySpec spec = ShopNearbyQuerySupport.buildSpec(pageNo, NEARBY_SEARCH_RADII);
            AmapSearchResultDTO amapResult = amapService.searchNearbyShopsWithMeta(
                    typeId,
                    null,
                    x,
                    y,
                    spec.getMaxRadius(),
                    spec.getPage(),
                    spec.getPageSize()
            );
            if (Boolean.TRUE.equals(amapResult.getSuccess())) {
                List<Shop> candidates = mergeShopLists(
                        queryFromRedisRaw(typeId, spec.getEnd(), x, y, spec.getMaxRadius()),
                        syncRealtimeShops(amapResult.getShops(), x, y),
                        queryFromDatabase(typeId, x, y, spec.getEnd(), spec.getMaxRadius(), cityCode)
                );
                if (candidates.isEmpty() && StrUtil.isNotBlank(cityCode)) {
                    shopSyncService.batchSync(typeId, cityCode, null);
                    candidates = mergeShopLists(
                            queryFromRedisRaw(typeId, spec.getEnd(), x, y, spec.getMaxRadius()),
                            queryFromDatabase(typeId, x, y, spec.getEnd(), spec.getMaxRadius(), cityCode)
                    );
                }
                return Result.ok(ShopNearbySupport.deduplicateAndLimit(candidates, SystemConstants.MAX_PAGE_SIZE));
            }

            List<Shop> candidates = ShopNearbyQuerySupport.mergeCandidates(
                    spec,
                    queryFromRedisRaw(typeId, spec.getEnd(), x, y, spec.getMaxRadius()),
                    () -> queryFromDatabase(typeId, x, y, spec.getEnd(), spec.getMaxRadius(), cityCode),
                    Collections::emptyList
            );
            if (candidates.isEmpty() && StrUtil.isNotBlank(cityCode)) {
                shopSyncService.batchSync(typeId, cityCode, null);
                candidates = queryFromDatabase(typeId, x, y, spec.getEnd(), spec.getMaxRadius(), cityCode);
            }
            return Result.ok(candidates);
        } catch (Exception e) {
            log.error("Query nearby shops failed. typeId={}, current={}, x={}, y={}, cityCode={}", typeId, current, x, y, cityCode, e);
            return Result.ok(Collections.emptyList());
        }
    }

    @Override
    public Result searchShopsByName(String name, Integer current, String cityCode) {
        if (StrUtil.isBlank(name)) {
            return Result.ok(Collections.emptyList());
        }

        int pageNo = current == null || current < 1 ? 1 : current;
        try {
            AmapSearchResultDTO amapResult = amapService.searchTextShopsWithMeta(
                    null,
                    name,
                    null,
                    null,
                    pageNo,
                    SystemConstants.MAX_PAGE_SIZE
            );
            if (Boolean.TRUE.equals(amapResult.getSuccess())) {
                List<Shop> merged = mergeShopLists(
                        queryLocalShopsByName(name, pageNo, null, null, cityCode),
                        syncRealtimeShops(amapResult.getShops(), null, null)
                );
                if (merged.isEmpty() && StrUtil.isNotBlank(cityCode)) {
                    shopSyncService.batchSync(null, cityCode, name);
                    merged = mergeShopLists(
                            queryLocalShopsByName(name, pageNo, null, null, cityCode),
                            syncRealtimeShops(amapResult.getShops(), null, null)
                    );
                }
                return Result.ok(ShopNearbySupport.deduplicateAndLimit(merged, SystemConstants.MAX_PAGE_SIZE));
            }
        } catch (Exception e) {
            log.warn("Search shops by name via AMap failed, name={}, current={}", name, pageNo, e);
        }

        List<Shop> fallback = queryLocalShopsByName(name, pageNo, null, null, cityCode);
        if (fallback.isEmpty() && StrUtil.isNotBlank(cityCode)) {
            shopSyncService.batchSync(null, cityCode, name);
            fallback = queryLocalShopsByName(name, pageNo, null, null, cityCode);
        }
        return Result.ok(fallback);
    }

    @Override
    public Result searchShopsForAssociation(String name, Double x, Double y, String cityCode) {
        if (StrUtil.isNotBlank(name)) {
            try {
                AmapSearchResultDTO amapResult = amapService.searchTextShopsWithMeta(
                        null,
                        name,
                        x,
                        y,
                        1,
                        SystemConstants.MAX_PAGE_SIZE
                );
                if (Boolean.TRUE.equals(amapResult.getSuccess())) {
                    List<Shop> merged = mergeShopLists(
                            queryCachedShops(name, x, y, cityCode),
                            queryLocalShopsByName(name, 1, x, y, cityCode),
                            syncRealtimeShops(amapResult.getShops(), x, y)
                    );
                    ShopNearbySupport.sortByDistance(merged);
                    if (merged.isEmpty() && StrUtil.isNotBlank(cityCode)) {
                        shopSyncService.batchSync(null, cityCode, name);
                        merged = mergeShopLists(
                                queryCachedShops(name, x, y, cityCode),
                                queryLocalShopsByName(name, 1, x, y, cityCode)
                        );
                        ShopNearbySupport.sortByDistance(merged);
                    }
                    return Result.ok(ShopNearbySupport.deduplicateAndLimit(merged, SystemConstants.MAX_PAGE_SIZE));
                }
            } catch (Exception e) {
                log.warn("Association search via AMap failed, name={}, x={}, y={}", name, x, y, e);
            }
        }

        List<Shop> cachedShops = queryCachedShops(name, x, y, cityCode);
        if (!cachedShops.isEmpty()) {
            return Result.ok(cachedShops);
        }

        LambdaQueryWrapper<Shop> associationWrapper = new LambdaQueryWrapper<>();
        associationWrapper.like(StrUtil.isNotBlank(name), Shop::getName, name)
                .orderByDesc(Shop::getUpdateTime)
                .last("limit " + SystemConstants.MAX_PAGE_SIZE);
        applyCityHint(associationWrapper, cityCode);
        List<Shop> dbShops = list(associationWrapper);
        dbShops.forEach(shop -> {
            ShopNearbySupport.fillDefaults(shop);
            ShopNearbySupport.applyDistance(shop, x, y);
        });
        ShopNearbySupport.sortByDistance(dbShops);
        return Result.ok(dbShops);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Shop ensureShopExists(Long shopId) {
        if (shopId == null) {
            return null;
        }

        Shop dbShop = getById(shopId);
        if (dbShop != null) {
            return dbShop;
        }

        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + shopId);
        if (StrUtil.isBlank(shopJson)) {
            return null;
        }

        Shop cachedShop = JSONUtil.toBean(shopJson, Shop.class);
        if (cachedShop == null) {
            return null;
        }

        cachedShop.setId(shopId);
        ShopNearbySupport.fillDefaults(cachedShop);
        saveOrUpdate(cachedShop);
        cacheShopGeo(cachedShop);
        cacheShopValue(cachedShop);
        return getById(shopId);
    }

    @Override
    public void refreshShopSearchCache(Shop shop) {
        if (shop == null || shop.getId() == null) {
            return;
        }
        ShopNearbySupport.fillDefaults(shop);
        cacheShopValue(shop);
        cacheShopGeo(shop);
    }

    private List<Shop> syncRealtimeShops(List<Shop> amapShops, Double x, Double y) {
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
                ShopNearbySupport.fillDefaults(amapShop);
                Shop localShop = shopSyncService.getOrSync(amapShop);
                Shop candidate = localShop != null ? localShop : amapShop;
                boolean hasStableLocalId = localShop != null
                        && localShop.getId() != null
                        && !localShop.getId().equals(amapShop.getId());
                ShopNearbySupport.fillDefaults(candidate);
                ShopNearbySupport.applyDistance(candidate, x, y);
                if (candidate.getId() == null || !seen.add(candidate.getId())) {
                    continue;
                }
                if (hasStableLocalId) {
                    cacheShopValue(candidate);
                    cacheShopGeo(candidate);
                }
                synced.add(candidate);
            } catch (Exception e) {
                log.warn("Sync realtime shop failed, keep transient result. shopId={}", amapShop.getId(), e);
                ShopNearbySupport.fillDefaults(amapShop);
                ShopNearbySupport.applyDistance(amapShop, x, y);
                if (amapShop.getId() != null && seen.add(amapShop.getId())) {
                    synced.add(amapShop);
                }
            }
        }
        return synced;
    }

    private List<Shop> mergeShopLists(List<Shop>... sources) {
        List<Shop> merged = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (sources == null) {
            return merged;
        }
        for (List<Shop> source : sources) {
            if (source == null || source.isEmpty()) {
                continue;
            }
            for (Shop shop : source) {
                if (shop == null) {
                    continue;
                }
                ShopNearbySupport.fillDefaults(shop);
                String key = buildUniqueShopKey(shop);
                if (seen.add(key)) {
                    merged.add(shop);
                }
            }
        }
        return merged;
    }

    private String buildUniqueShopKey(Shop shop) {
        if (shop == null) {
            return "";
        }
        if (shop.getId() != null) {
            return "id:" + shop.getId();
        }
        String name = StrUtil.blankToDefault(shop.getName(), "").trim().toLowerCase();
        String address = StrUtil.blankToDefault(shop.getAddress(), "").trim().toLowerCase();
        return "text:" + name + "|" + address;
    }

    private List<Shop> queryLocalShopsByName(String name, Integer current) {
        return queryLocalShopsByName(name, current, null, null, null);
    }

    private List<Shop> queryLocalShopsByName(String name, Integer current, Double x, Double y, String cityCode) {
        int pageNo = current == null || current < 1 ? 1 : current;
        LambdaQueryWrapper<Shop> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(condition -> condition.like(Shop::getName, name)
                .or()
                .like(Shop::getAddress, name)
                .or()
                .like(Shop::getArea, name));
        applyCityHint(wrapper, cityCode);
        Page<Shop> page = page(new Page<>(pageNo, SystemConstants.MAX_PAGE_SIZE), wrapper);
        page.getRecords().forEach(shop -> {
            ShopNearbySupport.fillDefaults(shop);
            ShopNearbySupport.applyDistance(shop, x, y);
        });
        return page.getRecords();
    }

    private List<Shop> queryFromRedisRaw(Integer typeId, int end, Double x, Double y, int radius) {
        if (end <= 0) {
            return Collections.emptyList();
        }
        try {
            String key = SHOP_GEO_KEY + typeId;
            GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                    .search(key,
                            GeoReference.fromCoordinate(x, y),
                            new Distance(radius),
                            RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                    );
            if (results == null) {
                return Collections.emptyList();
            }

            List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = results.getContent();
            if (content == null || content.isEmpty()) {
                return Collections.emptyList();
            }

            List<Long> ids = new ArrayList<>(content.size());
            Map<String, Distance> distanceMap = new HashMap<>();
            for (GeoResult<RedisGeoCommands.GeoLocation<String>> result : content) {
                if (result == null || result.getContent() == null) {
                    continue;
                }
                String shopId = result.getContent().getName();
                if (StrUtil.isBlank(shopId) || !StrUtil.isNumeric(shopId)) {
                    continue;
                }
                ids.add(Long.valueOf(shopId));
                if (result.getDistance() != null) {
                    distanceMap.put(shopId, result.getDistance());
                }
            }

            if (ids.isEmpty()) {
                return Collections.emptyList();
            }

            String join = StrUtil.join(",", ids);
            List<Shop> shopList = lambdaQuery()
                    .in(Shop::getId, ids)
                    .last("order by field(id," + join + ")")
                    .list();
            for (Shop shop : shopList) {
                ShopNearbySupport.fillDefaults(shop);
                Distance distance = distanceMap.get(String.valueOf(shop.getId()));
                if (distance != null) {
                    shop.setDistance(distance.getValue());
                }
            }
            return shopList;
        } catch (Exception e) {
            log.warn("Read raw geo shop cache failed. typeId={}, x={}, y={}, radius={}, end={}", typeId, x, y, radius, end, e);
            return Collections.emptyList();
        }
    }

    private List<Shop> queryFromDatabase(Integer typeId, Double x, Double y, int end, int radius, String cityCode) {
        int limit = Math.min(Math.max(end * 3, SystemConstants.MAX_PAGE_SIZE), 200);
        LambdaQueryWrapper<Shop> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Shop::getTypeId, typeId)
                .last("limit " + limit);
        applyCityHint(wrapper, cityCode);
        List<Shop> shops = list(wrapper);
        shops.forEach(shop -> {
            ShopNearbySupport.fillDefaults(shop);
            ShopNearbySupport.applyDistance(shop, x, y);
        });
        List<Shop> nearbyShops = shops.stream()
                .filter(shop -> shop.getDistance() != null)
                .filter(shop -> radius <= 0 || shop.getDistance() <= radius)
                .sorted(Comparator.comparing(shop -> shop.getDistance() == null ? Double.MAX_VALUE : shop.getDistance()))
                .collect(Collectors.toList());
        nearbyShops.forEach(this::cacheShopGeo);
        return nearbyShops;
    }

    private void cacheShopValue(Shop shop) {
        if (shop == null || shop.getId() == null) {
            return;
        }
        cacheClient.setShopCache(shop.getId(), shop);
    }

    private void cacheShopGeo(Shop shop) {
        if (shop == null || shop.getId() == null || shop.getTypeId() == null || shop.getX() == null || shop.getY() == null) {
            return;
        }
        stringRedisTemplate.opsForGeo().add(
                SHOP_GEO_KEY + shop.getTypeId(),
                new Point(shop.getX(), shop.getY()),
                shop.getId().toString()
        );
    }

    private List<Shop> queryCachedShops(String name, Double x, Double y, String cityCode) {
        Set<String> keys = stringRedisTemplate.keys(CACHE_SHOP_KEY + "*");
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }

        List<Shop> shops = new ArrayList<>();
        for (String key : keys) {
            String shopJson = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isBlank(shopJson)) {
                continue;
            }
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            if (shop == null || shop.getId() == null || StrUtil.isBlank(shop.getName())) {
                continue;
            }
            if (StrUtil.isNotBlank(name) && !StrUtil.containsIgnoreCase(shop.getName(), name)) {
                continue;
            }
            if (!matchesCity(shop, cityCode)) {
                continue;
            }
            ShopNearbySupport.fillDefaults(shop);
            ShopNearbySupport.applyDistance(shop, x, y);
            shops.add(shop);
        }
        return ShopNearbySupport.deduplicateAndLimit(shops, SystemConstants.MAX_PAGE_SIZE);
    }

    private void applyCityHint(LambdaQueryWrapper<Shop> wrapper, String cityCode) {
        if (wrapper == null || StrUtil.isBlank(cityCode)) {
            return;
        }
        CityProfileDTO cityProfile = cityService.matchCityProfile(cityCode, null);
        if (cityProfile == null || StrUtil.isBlank(cityProfile.getCityCode())) {
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

    private boolean matchesCity(Shop shop, String cityCode) {
        if (shop == null || StrUtil.isBlank(cityCode)) {
            return true;
        }
        CityProfileDTO cityProfile = cityService.matchCityProfile(cityCode, null);
        if (cityProfile == null) {
            return true;
        }
        String normalizedCityCode = StrUtil.blankToDefault(shop.getCityCode(), "");
        String adcode = StrUtil.blankToDefault(shop.getAdcode(), "");
        String cityName = StrUtil.blankToDefault(shop.getCity(), "");
        String address = StrUtil.blankToDefault(shop.getAddress(), "");
        String prefix = cityProfile.getCityCode().length() >= 4
                ? cityProfile.getCityCode().substring(0, 4)
                : cityProfile.getCityCode();
        return StrUtil.isBlank(normalizedCityCode)
                || cityProfile.getCityCode().equals(normalizedCityCode)
                || adcode.startsWith(prefix)
                || cityName.contains(cityProfile.getCityName())
                || address.contains(cityProfile.getCityName());
    }
}
