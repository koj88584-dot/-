package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IAmapService;
import com.hmdp.service.IBrowseHistoryService;
import com.hmdp.service.IShopService;
import com.hmdp.service.IShopSyncService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Point;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
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

    @Override
    public Result queryById(Long id) {
        //正常设置5分钟TTL
        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, 5L, TimeUnit.MINUTES);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        
        // 记录浏览历史（异步，不影响主流程）
        UserDTO user = UserHolder.getUser();
        if (user != null) {
            try {
                browseHistoryService.addHistory(1, id);
            } catch (Exception e) {
                // 忽略浏览历史记录失败
            }
        }
        
        return Result.ok(shop);
    }

    /**
     * 互斥锁解决缓存击穿
     *
     * @param id id
     * @return {@link Shop}
     */
    /*private Shop queryWithMutex(Long id) {
        String shopKey = CACHE_SHOP_KEY + id;
        //从redis中查询
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        //判断是否存在
        if (StringUtils.isNotEmpty(shopJson)) {
            //存在直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //判断控值
        if ("".equals(shopJson)) {
            return null;
        }
        //实现缓存重建
        //获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean isLock = tryLock(lockKey);
            //是否获取成功
            if (!isLock) {
                //获取失败 休眠并且重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //成功 通过id查询数据库
            shop = getById(id);
            //模拟重建延时
            Thread.sleep(200);
            if (shop == null) {
                //redis写入空值
                stringRedisTemplate.opsForValue().set(shopKey, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                //数据库不存在 返回错误
                return null;
            }
            //数据库存在 写入redis
            stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //释放互斥锁
            unLock(lockKey);
        }
        //返回
        return shop;
    }*/

    /**
     * 逻辑过期解决缓存击穿
     *
     * @param id id
     * @return {@link Shop}
     */
    /*private Shop queryWithLogicalExpire(Long id) {
        String shopKey = CACHE_SHOP_KEY + id;
        //从redis中查询
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        //判断是否存在
        if (StringUtils.isEmpty(shopJson)) {
            //不存在返回空
            return null;
        }
        //命中 反序列化
        RedisDate redisDate = JSONUtil.toBean(shopJson, RedisDate.class);
        JSONObject jsonObject = (JSONObject) redisDate.getData();
        Shop shop = BeanUtil.toBean(jsonObject, Shop.class);
        LocalDateTime expireTime = redisDate.getExpireTime();
        //判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            //未过期 直接返回
            return shop;
        }
        //已过期
        //获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean flag = tryLock(lockKey);
        //是否获取锁成功
        if (flag) {
            //成功 异步重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    this.saveShopToRedis(id, 20L);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unLock(lockKey);
                }
            });
        }
        //返回过期商铺信息
        return shop;
    }*/

    /**
     * 使用设置空值解决缓存穿透
     */
   /* private Shop queryWithPassThrough(Long id) {
        String shopKey = CACHE_SHOP_KEY + id;
        //从redis中查询
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        //判断是否存在
        if (StringUtils.isNotEmpty(shopJson)) {
            //存在直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //判断空值
        if ("".equals(shopJson)) {
            return null;
        }
        //不存在 查询数据库
        Shop shop = getById(id);
        if (shop == null) {
            //redis写入空值
            stringRedisTemplate.opsForValue().set(shopKey, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            //数据库不存在 返回错误
            return null;
        }
        //数据库存在 写入redis
        stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //返回
        return shop;
    }*/
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("id不能为空");
        }
        //更新数据库
        updateById(shop);
        //删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }

    @Resource
    private IAmapService amapService;
    @Resource
    private IShopSyncService shopSyncService;

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        try {
        //判断是否需要坐标查询
        if (x == null || y == null) {
            //不需要坐标查询
            Page<Shop> page = lambdaQuery()
                    .eq(Shop::getTypeId, typeId)
                    .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }

        int pageSize = SystemConstants.MAX_PAGE_SIZE;
        int page = current == null || current < 1 ? 1 : current;
        int end = page * pageSize;
        int maxRadius = NEARBY_SEARCH_RADII[NEARBY_SEARCH_RADII.length - 1];

        List<Shop> candidates = queryFromRedisRaw(typeId, end, x, y, maxRadius);
        Set<Long> seen = candidates.stream()
                .filter(Objects::nonNull)
                .map(Shop::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (candidates.size() < end) {
            List<Shop> dbCandidates = queryFromDatabase(typeId, x, y, end, maxRadius);
            mergeUnique(candidates, dbCandidates, seen);
        }

        if (candidates.size() < end) {
            List<Shop> amapCandidates = queryFromAmapAndSync(typeId, x, y, maxRadius, page, pageSize);
            mergeUnique(candidates, amapCandidates, seen);
        }

        candidates.sort(Comparator.comparing(shop -> shop.getDistance() == null ? Double.MAX_VALUE : shop.getDistance()));

        int from = (page - 1) * pageSize;
        if (from >= candidates.size()) {
            return Result.ok(Collections.emptyList());
        }
        int to = Math.min(from + pageSize, candidates.size());
        return Result.ok(candidates.subList(from, to));
        } catch (Exception e) {
            log.error("Query nearby shops failed. typeId={}, current={}, x={}, y={}", typeId, current, x, y, e);
            return Result.ok(Collections.emptyList());
        }
        
        // 首先尝试从Redis查询
        /*
        for (int radius : NEARBY_SEARCH_RADII) {
            List<Shop> shopList = queryFromRedis(typeId, current, x, y, radius);
            if (!shopList.isEmpty()) {
                return Result.ok(shopList);
            }
        }
        
        // Redis中没有数据，调用高德地图服务
        int fallbackRadius = NEARBY_SEARCH_RADII[NEARBY_SEARCH_RADII.length - 1];
        List<Shop> amapShops = amapService.searchNearbyShops(typeId, x, y, fallbackRadius);
        
        // 按照分页返回结果
        int from = (current - 1) * SystemConstants.MAX_PAGE_SIZE;
        int end = Math.min(from + SystemConstants.MAX_PAGE_SIZE, amapShops.size());
        
        if (from >= amapShops.size()) {
            return Result.ok(Collections.emptyList());
        }
        
        List<Shop> paginatedShops = amapShops.subList(from, end);
        return Result.ok(paginatedShops);
        */
    }

    @Override
    public Result searchShopsForAssociation(String name, Double x, Double y) {
        List<Shop> cachedShops = queryCachedShops(name, x, y);
        if (!cachedShops.isEmpty()) {
            return Result.ok(cachedShops);
        }

        List<Shop> dbShops = lambdaQuery()
                .like(StrUtil.isNotBlank(name), Shop::getName, name)
                .orderByDesc(Shop::getUpdateTime)
                .last("limit " + SystemConstants.MAX_PAGE_SIZE)
                .list();
        dbShops.forEach(shop -> applyDistance(shop, x, y));
        dbShops.sort(Comparator.comparing(shop -> shop.getDistance() == null ? Double.MAX_VALUE : shop.getDistance()));
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

        fillShopDefaults(cachedShop);
        saveOrUpdate(cachedShop);
        cacheShopGeo(cachedShop);
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + shopId, JSONUtil.toJsonStr(cachedShop), 30, TimeUnit.MINUTES);
        return getById(shopId);
    }
    
    /**
     * 从Redis查询店铺
     */
    private List<Shop> queryFromRedis(Integer typeId, Integer current, Double x, Double y, int radius) {
        try {
        //计算分页参数
        int from = (current - 1) * SystemConstants.MAX_PAGE_SIZE;
        int end = current * SystemConstants.MAX_PAGE_SIZE;
        //查询redis 距离排序 分页
        String key= SHOP_GEO_KEY+typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .search(key
                        , GeoReference.fromCoordinate(x, y)
                        , new Distance(radius)
                        , RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );
        //解析出id
        if (results==null){
            return Collections.emptyList();
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = results.getContent();
        if (content == null || content.size()<from){
            //没有下一页
            return Collections.emptyList();
        }
        //截取
        List<Long> ids=new ArrayList<>(content.size());
        Map<String,Distance> distanceMap=new HashMap<>();
        content.stream().skip(from).forEach(result->{
            if (result == null || result.getContent() == null) {
                return;
            }
            //店铺id
            String shopId = result.getContent().getName();
            if (StrUtil.isBlank(shopId) || !StrUtil.isNumeric(shopId)) {
                return;
            }
            ids.add(Long.valueOf(shopId));
            //距离
            Distance distance = result.getDistance();
            if (distance != null) {
                distanceMap.put(shopId,distance);
            }
        });

        //根据id查询shop
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        String join = StrUtil.join(",", ids);
        List<Shop> shopList = lambdaQuery().in(Shop::getId, ids)
                .last("order by field(id,"+join+")").list();
        for (Shop shop : shopList) {
            Distance distance = distanceMap.get(shop.getId().toString());
            if (distance != null) {
                shop.setDistance(distance.getValue());
            }
        }
        return shopList;
        } catch (Exception e) {
            log.warn("Read shop geo cache failed. typeId={}, current={}, x={}, y={}, radius={}", typeId, current, x, y, radius, e);
            return Collections.emptyList();
        }
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
        List<Shop> shopList = lambdaQuery().in(Shop::getId, ids)
                .last("order by field(id," + join + ")").list();
        for (Shop shop : shopList) {
            Distance distance = distanceMap.get(shop.getId().toString());
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

    private List<Shop> queryFromDatabase(Integer typeId, Double x, Double y, int end, int radius) {
        int limit = Math.min(Math.max(end * 3, SystemConstants.MAX_PAGE_SIZE), 200);
        List<Shop> shops = lambdaQuery()
                .eq(Shop::getTypeId, typeId)
                .last("limit " + limit)
                .list();
        shops.forEach(shop -> {
            fillShopDefaults(shop);
            applyDistance(shop, x, y);
        });
        List<Shop> nearbyShops = shops.stream()
                .filter(shop -> shop.getDistance() != null)
                .filter(shop -> radius <= 0 || shop.getDistance() <= radius)
                .sorted(Comparator.comparing(shop -> shop.getDistance() == null ? Double.MAX_VALUE : shop.getDistance()))
                .collect(Collectors.toList());
        for (Shop shop : nearbyShops) {
            cacheShopGeo(shop);
        }
        return nearbyShops;
    }

    private List<Shop> queryFromAmapAndSync(Integer typeId, Double x, Double y, int radius, int page, int pageSize) {
        List<Shop> amapShops = amapService.searchNearbyShops(typeId, null, x, y, radius, page, pageSize);
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
                fillShopDefaults(amapShop);
                Shop localShop = shopSyncService.getOrSync(amapShop);
                Shop candidate = localShop != null ? localShop : amapShop;
                fillShopDefaults(candidate);
                applyDistance(candidate, x, y);
                if (candidate.getId() == null || !seen.add(candidate.getId())) {
                    continue;
                }
                if (localShop != null) {
                    cacheShopValue(localShop);
                    cacheShopGeo(localShop);
                }
                synced.add(candidate);
            } catch (Exception e) {
                log.warn("Sync amap shop failed, keep transient result. shopId={}, typeId={}", amapShop.getId(), typeId, e);
                fillShopDefaults(amapShop);
                applyDistance(amapShop, x, y);
                if (amapShop.getId() != null && seen.add(amapShop.getId())) {
                    synced.add(amapShop);
                }
            }
        }
        return synced;
    }

    private void mergeUnique(List<Shop> target, List<Shop> source, Set<Long> seen) {
        if (source == null || source.isEmpty()) {
            return;
        }
        for (Shop shop : source) {
            if (shop == null || shop.getId() == null) {
                continue;
            }
            if (seen.add(shop.getId())) {
                target.add(shop);
            }
        }
    }

    private void cacheShopValue(Shop shop) {
        if (shop == null || shop.getId() == null) {
            return;
        }
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + shop.getId(),
                JSONUtil.toJsonStr(shop),
                30, TimeUnit.MINUTES);
    }

    private void cacheShopGeo(Shop shop) {
        if (shop == null || shop.getId() == null || shop.getTypeId() == null || shop.getX() == null || shop.getY() == null) {
            return;
        }
        String key = SHOP_GEO_KEY + shop.getTypeId();
        stringRedisTemplate.opsForGeo().add(key, new Point(shop.getX(), shop.getY()), shop.getId().toString());
    }

    private List<Shop> queryCachedShops(String name, Double x, Double y) {
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
            applyDistance(shop, x, y);
            shops.add(shop);
        }

        Map<Long, Shop> deduplicated = new LinkedHashMap<>();
        for (Shop shop : shops) {
            deduplicated.putIfAbsent(shop.getId(), shop);
        }

        List<Shop> result = new ArrayList<>(deduplicated.values());
        result.sort(Comparator.comparing(shop -> shop.getDistance() == null ? Double.MAX_VALUE : shop.getDistance()));
        if (result.size() > SystemConstants.MAX_PAGE_SIZE) {
            return result.subList(0, SystemConstants.MAX_PAGE_SIZE);
        }
        return result;
    }

    private void fillShopDefaults(Shop shop) {
        LocalDateTime now = LocalDateTime.now();
        if (StrUtil.isBlank(shop.getImages())) {
            shop.setImages("/imgs/shop/default.png");
        }
        if (StrUtil.isBlank(shop.getAddress())) {
            shop.setAddress("暂无地址");
        }
        if (shop.getSold() == null) {
            shop.setSold(0);
        }
        if (shop.getComments() == null) {
            shop.setComments(0);
        }
        if (shop.getScore() == null || shop.getScore() <= 0) {
            shop.setScore(40);
        }
        if (StrUtil.isBlank(shop.getOpenHours())) {
            shop.setOpenHours("09:00-22:00");
        }
        if (shop.getCreateTime() == null) {
            shop.setCreateTime(now);
        }
        shop.setUpdateTime(now);
    }

    private void applyDistance(Shop shop, Double x, Double y) {
        if (shop == null || x == null || y == null || shop.getX() == null || shop.getY() == null) {
            return;
        }
        shop.setDistance(calculateDistance(x, y, shop.getX(), shop.getY()));
    }

    private double calculateDistance(double lng1, double lat1, double lng2, double lat2) {
        double radLat1 = Math.toRadians(lat1);
        double radLat2 = Math.toRadians(lat2);
        double a = radLat1 - radLat2;
        double b = Math.toRadians(lng1) - Math.toRadians(lng2);
        double s = 2 * Math.asin(Math.sqrt(Math.pow(Math.sin(a / 2), 2) +
                Math.cos(radLat1) * Math.cos(radLat2) * Math.pow(Math.sin(b / 2), 2)));
        s = s * 6378137;
        return s;
    }

//    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /*    *//**
     * 获取锁
     *
     * @param key 关键
     * @return boolean
     *//*
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    *//**
     * 释放锁
     *
     * @param key 关键
     *//*
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }

    *//**
     * 存入redis 携带逻辑过期时间
     *//*
    public void saveShopToRedis(Long id, Long expireSeconds) throws InterruptedException {
        //查询店铺数据
        Shop shop = getById(id);
        Thread.sleep(200);
        //封装逻辑过期
        RedisDate redisDate = new RedisDate();
        redisDate.setData(shop);
        redisDate.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //写了redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisDate));
    }*/
}
