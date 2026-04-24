package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.AmapSearchResultDTO;
import com.hmdp.dto.CityProfileDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopExtend;
import com.hmdp.mapper.ShopExtendMapper;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IAmapService;
import com.hmdp.service.ICityService;
import com.hmdp.service.IShopSyncService;
import com.hmdp.utils.CacheClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_SYNC_KEY;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_SYNC_TTL;
import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;
import static com.hmdp.utils.RedisConstants.SHOP_REFRESH_KEY;
import static com.hmdp.utils.RedisConstants.SHOP_REFRESH_TTL_MINUTES;
import static com.hmdp.utils.RedisConstants.SHOP_SYNC_KEY;

/**
 * 店铺同步服务实现。
 * 正常查询返回高德实时数据，但会同步建立稳定的本地店铺 ID，并异步刷新镜像数据。
 */
@Slf4j
@Service
public class ShopSyncServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopSyncService {

    private static final ExecutorService SHOP_MIRROR_REFRESH_EXECUTOR = Executors.newFixedThreadPool(4);

    @Resource
    private ShopExtendMapper shopExtendMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Resource
    private IAmapService amapService;

    @Resource
    private ICityService cityService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Shop syncToLocal(Shop amapShop) {
        return ensureMappedShop(amapShop);
    }

    @Override
    public Shop queryByAmapPoiId(String amapPoiId) {
        if (StrUtil.isBlank(amapPoiId)) {
            return null;
        }

        Long localShopId = resolveLocalShopId(amapPoiId);
        if (localShopId == null) {
            return null;
        }

        Shop localShop = getById(localShopId);
        if (localShop != null) {
            return localShop;
        }

        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + localShopId);
        if (StrUtil.isBlank(shopJson)) {
            return null;
        }
        Shop cachedShop = JSONUtil.toBean(shopJson, Shop.class);
        if (cachedShop != null) {
            cachedShop.setId(localShopId);
        }
        return cachedShop;
    }

    @Override
    public boolean isSynced(Long amapShopId) {
        if (amapShopId == null) {
            return false;
        }
        return resolveLocalShopId(String.valueOf(amapShopId)) != null;
    }

    @Override
    public Shop getOrSync(Shop amapShop) {
        Shop mappedShop = ensureMappedShop(amapShop);
        if (mappedShop != null) {
            refreshMirrorAsync(mappedShop);
        }
        return mappedShop;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Shop ensureMappedShop(Shop amapShop) {
        if (amapShop == null) {
            return null;
        }
        String amapPoiId = resolveAmapPoiId(amapShop);
        if (StrUtil.isBlank(amapPoiId)) {
            return amapShop;
        }

        Long localShopId = resolveLocalShopId(amapPoiId);
        if (localShopId != null) {
            return bindLocalIdentity(amapShop, localShopId);
        }

        String lockKey = LOCK_SHOP_SYNC_KEY + amapPoiId;
        boolean locked = tryLock(lockKey, LOCK_SHOP_SYNC_TTL, TimeUnit.SECONDS);
        if (!locked) {
            sleepQuietly(80L);
            Long retryLocalId = resolveLocalShopId(amapPoiId);
            return retryLocalId == null ? amapShop : bindLocalIdentity(amapShop, retryLocalId);
        }

        try {
            Long existingLocalId = resolveLocalShopId(amapPoiId);
            if (existingLocalId != null) {
                return bindLocalIdentity(amapShop, existingLocalId);
            }

            Shop localShop = buildLocalMirrorShop(amapShop);
            save(localShop);

            ShopExtend extend = new ShopExtend();
            extend.setShopId(localShop.getId());
            extend.setSource("amap");
            extend.setAmapPoiId(amapPoiId);
            extend.setBusinessArea(localShop.getArea());
            extend.setRating(toRating(localShop.getScore()));
            extend.setCost(toCost(localShop.getAvgPrice()));
            extend.setCreateTime(LocalDateTime.now());
            extend.setUpdateTime(LocalDateTime.now());
            shopExtendMapper.insert(extend);

            cacheSyncMapping(amapPoiId, localShop.getId());
            cacheLocalMirror(bindLocalIdentity(localShop, localShop.getId()));
            log.info("Mapped AMap shop to local mirror, poiId={}, localShopId={}", amapPoiId, localShop.getId());
            return bindLocalIdentity(amapShop, localShop.getId());
        } finally {
            stringRedisTemplate.delete(lockKey);
        }
    }

    @Override
    public void refreshMirrorAsync(Shop amapShop) {
        if (amapShop == null) {
            return;
        }
        String amapPoiId = resolveAmapPoiId(amapShop);
        if (StrUtil.isBlank(amapPoiId)) {
            return;
        }
        String refreshKey = SHOP_REFRESH_KEY + amapPoiId;
        if (!tryLock(refreshKey, SHOP_REFRESH_TTL_MINUTES, TimeUnit.MINUTES)) {
            return;
        }
        SHOP_MIRROR_REFRESH_EXECUTOR.submit(() -> {
            try {
                refreshMirror(amapShop);
            } catch (Exception e) {
                log.warn("Refresh shop mirror failed, poiId={}", amapPoiId, e);
            }
        });
    }

    @Override
    public Result triggerSync(Long shopId, String action) {
        if (shopId == null) {
            return Result.fail("店铺ID不能为空");
        }

        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + shopId);
        if (StrUtil.isBlank(shopJson)) {
            return getById(shopId) != null ? Result.ok(shopId) : Result.fail("店铺数据已过期，请重新搜索");
        }

        Shop cachedShop = JSONUtil.toBean(shopJson, Shop.class);
        if (cachedShop == null) {
            return Result.fail("店铺数据已损坏，请重新搜索");
        }

        Shop localShop = getOrSync(cachedShop);
        if (localShop == null) {
            return Result.fail("同步失败");
        }

        if (Objects.equals("view", action)) {
            return Result.ok(localShop.getId());
        }
        return Result.ok(localShop.getId());
    }

    @Override
    public Result batchSync(Integer typeId, String cityCode, String keywords) {
        CityProfileDTO cityProfile = cityService.matchCityProfile(cityCode, null);
        if (cityProfile == null || cityProfile.getLongitude() == null || cityProfile.getLatitude() == null) {
            return Result.fail("鏈壘鍒板彲鐢ㄧ殑鍩庡競閰嶇疆");
        }

        java.util.LinkedHashSet<String> searchKeywords = new java.util.LinkedHashSet<>();
        if (StrUtil.isNotBlank(keywords)) {
            for (String item : keywords.split("[,，|]")) {
                if (StrUtil.isNotBlank(item)) {
                    searchKeywords.add(item.trim());
                }
            }
        }

        if (searchKeywords.isEmpty()) {
            if (typeId != null) {
                searchKeywords.add(cityProfile.getCityName() + " " + resolveTypeKeyword(typeId));
            }
            for (String scene : cityProfile.getDefaultScenes()) {
                if (searchKeywords.size() >= 4) {
                    break;
                }
                searchKeywords.add(cityProfile.getCityName() + " " + scene);
            }
            for (String hotSearch : cityProfile.getHotSearches()) {
                if (searchKeywords.size() >= 6) {
                    break;
                }
                searchKeywords.add(hotSearch);
            }
        }

        java.util.LinkedHashSet<Long> syncedIds = new java.util.LinkedHashSet<>();
        for (String searchKeyword : searchKeywords) {
            AmapSearchResultDTO result = amapService.searchTextShopsWithMeta(
                    typeId,
                    searchKeyword,
                    cityProfile.getLongitude(),
                    cityProfile.getLatitude(),
                    1,
                    12
            );
            if (result == null || result.getShops() == null || result.getShops().isEmpty()) {
                continue;
            }
            for (Shop shop : result.getShops()) {
                Shop localShop = ensureMappedShop(shop);
                if (localShop != null && localShop.getId() != null) {
                    syncedIds.add(localShop.getId());
                }
            }
        }

        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("cityCode", cityProfile.getCityCode());
        payload.put("cityName", cityProfile.getCityName());
        payload.put("keywords", new java.util.ArrayList<>(searchKeywords));
        payload.put("syncedCount", syncedIds.size());
        return Result.ok(payload);
    }

    private void refreshMirror(Shop amapShop) {
        String amapPoiId = resolveAmapPoiId(amapShop);
        if (StrUtil.isBlank(amapPoiId)) {
            return;
        }
        Long localShopId = resolveLocalShopId(amapPoiId);
        if (localShopId == null) {
            Shop ensuredShop = ensureMappedShop(amapShop);
            localShopId = ensuredShop == null ? null : ensuredShop.getId();
        }
        if (localShopId == null) {
            return;
        }

        Shop existing = getById(localShopId);
        if (existing == null) {
            return;
        }

        existing.setName(StrUtil.blankToDefault(amapShop.getName(), existing.getName()));
        existing.setTypeId(amapShop.getTypeId() != null ? amapShop.getTypeId() : existing.getTypeId());
        existing.setImages(StrUtil.blankToDefault(amapShop.getImages(), existing.getImages()));
        existing.setArea(StrUtil.blankToDefault(amapShop.getArea(), existing.getArea()));
        existing.setAddress(StrUtil.blankToDefault(amapShop.getAddress(), existing.getAddress()));
        existing.setCityCode(StrUtil.blankToDefault(amapShop.getCityCode(), existing.getCityCode()));
        existing.setProvince(StrUtil.blankToDefault(amapShop.getProvince(), existing.getProvince()));
        existing.setCity(StrUtil.blankToDefault(amapShop.getCity(), existing.getCity()));
        existing.setDistrict(StrUtil.blankToDefault(amapShop.getDistrict(), existing.getDistrict()));
        existing.setAdcode(StrUtil.blankToDefault(amapShop.getAdcode(), existing.getAdcode()));
        existing.setX(amapShop.getX() != null ? amapShop.getX() : existing.getX());
        existing.setY(amapShop.getY() != null ? amapShop.getY() : existing.getY());
        existing.setAvgPrice(amapShop.getAvgPrice() != null ? amapShop.getAvgPrice() : existing.getAvgPrice());
        existing.setSold(amapShop.getSold() != null ? amapShop.getSold() : existing.getSold());
        existing.setComments(amapShop.getComments() != null ? amapShop.getComments() : existing.getComments());
        existing.setScore(amapShop.getScore() != null ? amapShop.getScore() : existing.getScore());
        existing.setOpenHours(StrUtil.blankToDefault(amapShop.getOpenHours(), existing.getOpenHours()));
        ShopNearbySupport.fillDefaults(existing);
        updateById(existing);

        ShopExtend extend = queryExtendByAmapPoiId(amapPoiId);
        if (extend == null) {
            extend = new ShopExtend();
            extend.setShopId(localShopId);
            extend.setSource("amap");
            extend.setAmapPoiId(amapPoiId);
            extend.setCreateTime(LocalDateTime.now());
        }
        extend.setBusinessArea(existing.getArea());
        extend.setRating(toRating(existing.getScore()));
        extend.setCost(toCost(existing.getAvgPrice()));
        extend.setUpdateTime(LocalDateTime.now());
        if (extend.getId() == null) {
            shopExtendMapper.insert(extend);
        } else {
            shopExtendMapper.updateById(extend);
        }

        cacheSyncMapping(amapPoiId, localShopId);
        cacheLocalMirror(bindLocalIdentity(amapShop, localShopId));
    }

    private Shop buildLocalMirrorShop(Shop source) {
        Shop localShop = new Shop();
        localShop.setName(source.getName());
        localShop.setTypeId(source.getTypeId());
        localShop.setImages(source.getImages());
        localShop.setArea(source.getArea());
        localShop.setAddress(source.getAddress());
        localShop.setCityCode(source.getCityCode());
        localShop.setProvince(source.getProvince());
        localShop.setCity(source.getCity());
        localShop.setDistrict(source.getDistrict());
        localShop.setAdcode(source.getAdcode());
        localShop.setX(source.getX());
        localShop.setY(source.getY());
        localShop.setAvgPrice(source.getAvgPrice());
        localShop.setSold(source.getSold());
        localShop.setComments(source.getComments());
        localShop.setScore(source.getScore());
        localShop.setOpenHours(source.getOpenHours());
        ShopNearbySupport.fillDefaults(localShop);
        return localShop;
    }

    private Shop bindLocalIdentity(Shop source, Long localShopId) {
        if (source == null || localShopId == null) {
            return source;
        }
        Shop mappedShop = new Shop();
        mappedShop.setId(localShopId);
        mappedShop.setName(source.getName());
        mappedShop.setTypeId(source.getTypeId());
        mappedShop.setImages(source.getImages());
        mappedShop.setArea(source.getArea());
        mappedShop.setAddress(source.getAddress());
        mappedShop.setCityCode(source.getCityCode());
        mappedShop.setProvince(source.getProvince());
        mappedShop.setCity(source.getCity());
        mappedShop.setDistrict(source.getDistrict());
        mappedShop.setAdcode(source.getAdcode());
        mappedShop.setX(source.getX());
        mappedShop.setY(source.getY());
        mappedShop.setAvgPrice(source.getAvgPrice());
        mappedShop.setSold(source.getSold());
        mappedShop.setComments(source.getComments());
        mappedShop.setScore(source.getScore());
        mappedShop.setOpenHours(source.getOpenHours());
        mappedShop.setDistance(source.getDistance());
        mappedShop.setCreateTime(source.getCreateTime());
        mappedShop.setUpdateTime(source.getUpdateTime());
        mappedShop.setAmapPoiId(resolveAmapPoiId(source));
        ShopNearbySupport.fillDefaults(mappedShop);
        return mappedShop;
    }

    private void cacheLocalMirror(Shop shop) {
        if (shop == null || shop.getId() == null) {
            return;
        }
        cacheClient.setShopCache(shop.getId(), shop);
        if (shop.getTypeId() != null && shop.getX() != null && shop.getY() != null) {
            stringRedisTemplate.opsForGeo().add(
                    SHOP_GEO_KEY + shop.getTypeId(),
                    new org.springframework.data.geo.Point(shop.getX(), shop.getY()),
                    String.valueOf(shop.getId())
            );
        }
    }

    private Long resolveLocalShopId(String amapPoiId) {
        if (StrUtil.isBlank(amapPoiId)) {
            return null;
        }
        String cachedValue = stringRedisTemplate.opsForValue().get(SHOP_SYNC_KEY + amapPoiId);
        if (StrUtil.isNotBlank(cachedValue) && StrUtil.isNumeric(cachedValue)) {
            return Long.valueOf(cachedValue);
        }
        ShopExtend extend = queryExtendByAmapPoiId(amapPoiId);
        if (extend == null || extend.getShopId() == null) {
            return null;
        }
        cacheSyncMapping(amapPoiId, extend.getShopId());
        return extend.getShopId();
    }

    private ShopExtend queryExtendByAmapPoiId(String amapPoiId) {
        if (StrUtil.isBlank(amapPoiId)) {
            return null;
        }
        return shopExtendMapper.selectOne(new QueryWrapper<ShopExtend>().eq("amap_poi_id", amapPoiId));
    }

    private void cacheSyncMapping(String amapPoiId, Long localShopId) {
        if (StrUtil.isBlank(amapPoiId) || localShopId == null) {
            return;
        }
        stringRedisTemplate.opsForValue().set(SHOP_SYNC_KEY + amapPoiId, String.valueOf(localShopId), 7, TimeUnit.DAYS);
    }

    private String resolveAmapPoiId(Shop shop) {
        if (shop == null) {
            return null;
        }
        if (StrUtil.isNotBlank(shop.getAmapPoiId())) {
            return shop.getAmapPoiId();
        }
        if (shop.getId() != null && shop.getId() >= 9_000_000_000L) {
            return String.valueOf(shop.getId());
        }
        return null;
    }

    private boolean tryLock(String key, long ttl, TimeUnit unit) {
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", ttl, unit);
        return Boolean.TRUE.equals(locked);
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private BigDecimal toRating(Integer score) {
        if (score == null) {
            return null;
        }
        return BigDecimal.valueOf(score).divide(BigDecimal.TEN);
    }

    private BigDecimal toCost(Long avgPrice) {
        if (avgPrice == null) {
            return null;
        }
        return BigDecimal.valueOf(avgPrice);
    }

    private String resolveTypeKeyword(Integer typeId) {
        if (typeId == null) {
            return "热门好店";
        }
        switch (typeId) {
            case 1:
                return "美食";
            case 2:
                return "KTV";
            case 3:
                return "丽人";
            case 4:
                return "健身运动";
            case 5:
                return "按摩足疗";
            case 6:
                return "美容SPA";
            case 7:
                return "亲子游乐";
            case 8:
                return "酒吧";
            case 9:
                return "轰趴馆";
            case 10:
                return "美睫美甲";
            default:
                return "热门好店";
        }
    }
}
