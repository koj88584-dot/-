package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.*;

import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

/**
 * 地图可视化控制器
 */
@RestController
@RequestMapping("/map")
public class MapController {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IShopService shopService;

    /**
     * 获取附近店铺（用于地图显示）
     * @param longitude 经度
     * @param latitude 纬度
     * @param radius 半径（米）
     * @param typeId 店铺类型，可选
     */
    @GetMapping("/nearby")
    public Result getNearbyShops(@RequestParam("longitude") Double longitude,
                                  @RequestParam("latitude") Double latitude,
                                  @RequestParam(value = "radius", defaultValue = "5000") Double radius,
                                  @RequestParam(value = "typeId", required = false) Integer typeId) {
        List<Map<String, Object>> result = new ArrayList<>();

        if (typeId != null) {
            // 查询指定类型的店铺
            String key = SHOP_GEO_KEY + typeId;
            GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                    .search(key,
                            GeoReference.fromCoordinate(longitude, latitude),
                            new Distance(radius, RedisGeoCommands.DistanceUnit.METERS),
                            RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs()
                                    .includeDistance()
                                    .includeCoordinates()
                                    .sortAscending()
                    );

            if (results != null) {
                for (GeoResult<RedisGeoCommands.GeoLocation<String>> geoResult : results.getContent()) {
                    Map<String, Object> shopMap = buildShopMap(geoResult);
                    if (shopMap != null) {
                        result.add(shopMap);
                    }
                }
            }
        } else {
            // 查询所有类型的店铺
            Set<String> keys = stringRedisTemplate.keys(SHOP_GEO_KEY + "*");
            if (keys != null) {
                for (String key : keys) {
                    try {
                        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                                .search(key,
                                        GeoReference.fromCoordinate(longitude, latitude),
                                        new Distance(radius, RedisGeoCommands.DistanceUnit.METERS),
                                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs()
                                                .includeDistance()
                                                .includeCoordinates()
                                                .sortAscending()
                                );

                        if (results != null) {
                            for (GeoResult<RedisGeoCommands.GeoLocation<String>> geoResult : results.getContent()) {
                                Map<String, Object> shopMap = buildShopMap(geoResult);
                                if (shopMap != null && !containsShop(result, (Long) shopMap.get("id"))) {
                                    result.add(shopMap);
                                }
                            }
                        }
                    } catch (Exception e) {
                        // 忽略错误，继续查询其他类型
                    }
                }
            }
        }

        // 按距离排序
        result.sort(Comparator.comparingDouble(s -> (Double) s.get("distance")));

        return Result.ok(result);
    }

    /**
     * 获取店铺详情（用于地图弹窗）
     * @param shopId 店铺id
     */
    @GetMapping("/shop/{shopId}")
    public Result getShopDetailForMap(@PathVariable("shopId") Long shopId) {
        Shop shop = shopService.getById(shopId);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("id", shop.getId());
        result.put("name", shop.getName());
        result.put("address", shop.getAddress());
        result.put("score", shop.getScore());
        result.put("avgPrice", shop.getAvgPrice());
        result.put("sold", shop.getSold());
        result.put("images", shop.getImages());
        result.put("longitude", shop.getX());
        result.put("latitude", shop.getY());

        return Result.ok(result);
    }

    /**
     * 搜索店铺（地图搜索）
     * @param keyword 关键词
     * @param longitude 经度
     * @param latitude 纬度
     * @param radius 半径
     */
    @GetMapping("/search")
    public Result searchShopsOnMap(@RequestParam("keyword") String keyword,
                                    @RequestParam("longitude") Double longitude,
                                    @RequestParam("latitude") Double latitude,
                                    @RequestParam(value = "radius", defaultValue = "10000") Double radius) {
        // 先搜索匹配的店铺
        List<Shop> shops = shopService.lambdaQuery()
                .like(Shop::getName, keyword)
                .or()
                .like(Shop::getAddress, keyword)
                .list();

        List<Map<String, Object>> result = new ArrayList<>();

        for (Shop shop : shops) {
            // 计算距离
            double distance = calculateDistance(longitude, latitude, shop.getX(), shop.getY());
            if (distance <= radius) {
                Map<String, Object> shopMap = new HashMap<>();
                shopMap.put("id", shop.getId());
                shopMap.put("name", shop.getName());
                shopMap.put("address", shop.getAddress());
                shopMap.put("score", shop.getScore());
                shopMap.put("avgPrice", shop.getAvgPrice());
                shopMap.put("sold", shop.getSold());
                shopMap.put("images", shop.getImages());
                shopMap.put("longitude", shop.getX());
                shopMap.put("latitude", shop.getY());
                shopMap.put("distance", distance);
                result.add(shopMap);
            }
        }

        // 按距离排序
        result.sort(Comparator.comparingDouble(s -> (Double) s.get("distance")));

        return Result.ok(result);
    }

    /**
     * 计算两点之间的距离（米）
     */
    private double calculateDistance(double lng1, double lat1, double lng2, double lat2) {
        double radLat1 = Math.toRadians(lat1);
        double radLat2 = Math.toRadians(lat2);
        double a = radLat1 - radLat2;
        double b = Math.toRadians(lng1) - Math.toRadians(lng2);
        double s = 2 * Math.asin(Math.sqrt(Math.pow(Math.sin(a / 2), 2) +
                Math.cos(radLat1) * Math.cos(radLat2) * Math.pow(Math.sin(b / 2), 2)));
        s = s * 6378137; // 地球半径（米）
        return s;
    }

    private Map<String, Object> buildShopMap(GeoResult<RedisGeoCommands.GeoLocation<String>> geoResult) {
        String shopIdStr = geoResult.getContent().getName();
        try {
            Long shopId = Long.valueOf(shopIdStr);
            Shop shop = shopService.getById(shopId);
            if (shop == null) {
                return null;
            }

            Map<String, Object> shopMap = new HashMap<>();
            shopMap.put("id", shop.getId());
            shopMap.put("name", shop.getName());
            shopMap.put("address", shop.getAddress());
            shopMap.put("score", shop.getScore());
            shopMap.put("avgPrice", shop.getAvgPrice());
            shopMap.put("sold", shop.getSold());
            shopMap.put("images", shop.getImages());
            shopMap.put("longitude", shop.getX());
            shopMap.put("latitude", shop.getY());
            shopMap.put("distance", geoResult.getDistance().getValue());

            return shopMap;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean containsShop(List<Map<String, Object>> list, Long shopId) {
        return list.stream().anyMatch(m -> shopId.equals(m.get("id")));
    }
}
