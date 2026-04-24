package com.hmdp.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.config.AmapConfig;
import com.hmdp.dto.CityProfileDTO;
import com.hmdp.dto.LocationContextDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.service.IAmapService;
import com.hmdp.service.ICityService;
import com.hmdp.service.IShopService;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

@RestController
@RequestMapping("/map")
public class MapController {

    private static final String AMAP_REVERSE_GEOCODE_URL = "https://restapi.amap.com/v3/geocode/regeo";
    private static final String AMAP_PLACE_TEXT_URL = "https://restapi.amap.com/v3/place/text";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IShopService shopService;

    @Resource
    private AmapConfig amapConfig;

    @Resource
    private IAmapService amapService;

    @Resource
    private ICityService cityService;

    @GetMapping("/amap-config")
    public Result getAmapWebConfig() {
        Map<String, Object> data = new HashMap<>();
        data.put("webKey", StrUtil.blankToDefault(amapConfig.getWebKey(), ""));
        data.put("securityJsCode", StrUtil.blankToDefault(amapConfig.getSecurityJsCode(), ""));
        data.put("configured", StrUtil.isNotBlank(amapConfig.getWebKey()));
        return Result.ok(data);
    }

    @GetMapping("/regeo")
    public Result reverseGeocode(@RequestParam("longitude") Double longitude,
                                 @RequestParam("latitude") Double latitude) {
        if (longitude == null || latitude == null) {
            return Result.fail("经纬度不能为空");
        }
        LocationContextDTO context = enrichLocationContext(
                amapService.reverseGeocode(longitude, latitude, null, "legacy-regeo")
        );
        if (!Boolean.TRUE.equals(context.getAmapAvailable())) {
            return Result.ok(Collections.emptyMap());
        }
        return Result.ok(buildRegeoPayload(context));
    }

    @GetMapping("/location/resolve")
    public Result resolveLocation(@RequestParam("longitude") Double longitude,
                                  @RequestParam("latitude") Double latitude,
                                  @RequestParam(value = "accuracy", required = false) Integer accuracy,
                                  @RequestParam(value = "source", defaultValue = "browser") String source) {
        if (longitude == null || latitude == null) {
            return Result.fail("经纬度不能为空");
        }
        return Result.ok(enrichLocationContext(amapService.reverseGeocode(longitude, latitude, accuracy, source)));
    }

    @GetMapping("/location/ip")
    public Result resolveIpLocation(@RequestParam(value = "ip", required = false) String ip,
                                    HttpServletRequest request) {
        String clientIp = StrUtil.blankToDefault(ip, resolveClientIp(request));
        return Result.ok(enrichLocationContext(amapService.locateByIp(clientIp)));
    }

    static Map<String, Object> buildRegeoPayload(JSONObject regeocode) {
        if (regeocode == null) {
            return Collections.emptyMap();
        }
        JSONObject addressComponent = regeocode.getJSONObject("addressComponent");
        Map<String, Object> data = new HashMap<>();
        data.put("city", addressComponent == null ? "" : StrUtil.blankToDefault(addressComponent.getStr("city"), addressComponent.getStr("province")));
        data.put("province", addressComponent == null ? "" : addressComponent.getStr("province"));
        data.put("district", addressComponent == null ? "" : addressComponent.getStr("district"));
        data.put("adcode", addressComponent == null ? "" : addressComponent.getStr("adcode"));
        data.put("formattedAddress", regeocode.getStr("formatted_address"));
        return data;
    }

    private Map<String, Object> buildRegeoPayload(LocationContextDTO context) {
        Map<String, Object> data = new HashMap<>();
        if (context == null) {
            return data;
        }
        data.put("city", context.getCity());
        data.put("province", context.getProvince());
        data.put("district", context.getDistrict());
        data.put("adcode", context.getAdcode());
        data.put("cityCode", context.getCityCode());
        data.put("formattedAddress", context.getFormattedAddress());
        data.put("cityEditionEnabled", context.getCityEditionEnabled());
        data.put("cityProfile", context.getCityProfile());
        return data;
    }

    @GetMapping("/place-text")
    public Result searchPlaceText(@RequestParam("keyword") String keyword,
                                  @RequestParam(value = "offset", defaultValue = "10") Integer offset,
                                  @RequestParam(value = "page", defaultValue = "1") Integer page,
                                  @RequestParam(value = "citylimit", defaultValue = "true") Boolean cityLimit,
                                  @RequestParam(value = "city", required = false) String city) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("pois", Collections.emptyList());
        payload.put("count", 0);
        if (StrUtil.isBlank(keyword) || StrUtil.isBlank(amapConfig.getKey())) {
            return Result.ok(payload);
        }

        Map<String, Object> params = new HashMap<>();
        params.put("key", amapConfig.getKey());
        params.put("keywords", keyword.trim());
        params.put("citylimit", Boolean.TRUE.equals(cityLimit));
        params.put("offset", offset == null || offset < 1 ? 10 : offset);
        params.put("page", page == null || page < 1 ? 1 : page);
        params.put("extensions", "all");
        if (StrUtil.isNotBlank(city)) {
            params.put("city", city.trim());
        }

        try {
            JSONObject jsonObject = callAmap(AMAP_PLACE_TEXT_URL, params);
            JSONArray pois = jsonObject.getJSONArray("pois");
            List<Map<String, Object>> items = new ArrayList<>();
            if (pois != null) {
                for (int i = 0; i < pois.size(); i++) {
                    JSONObject poi = pois.getJSONObject(i);
                    Map<String, Object> item = new HashMap<>();
                    item.put("name", poi.getStr("name"));
                    item.put("address", poi.getStr("address"));
                    item.put("pname", poi.getStr("pname"));
                    item.put("cityname", poi.getStr("cityname"));
                    item.put("adname", poi.getStr("adname"));
                    item.put("adcode", poi.getStr("adcode"));
                    item.put("location", poi.getStr("location"));
                    String location = poi.getStr("location");
                    if (StrUtil.isNotBlank(location) && location.contains(",")) {
                        String[] coords = location.split(",");
                        item.put("x", coords[0]);
                        item.put("y", coords[1]);
                    }
                    items.add(item);
                }
            }
            payload.put("pois", items);
            payload.put("count", jsonObject.getInt("count", items.size()));
            return Result.ok(payload);
        } catch (Exception e) {
            return Result.ok(payload);
        }
    }

    @GetMapping("/nearby")
    public Result getNearbyShops(@RequestParam("longitude") Double longitude,
                                 @RequestParam("latitude") Double latitude,
                                 @RequestParam(value = "radius", defaultValue = "5000") Double radius,
                                 @RequestParam(value = "typeId", required = false) Integer typeId) {
        List<Map<String, Object>> result = new ArrayList<>();

        if (typeId != null) {
            String key = SHOP_GEO_KEY + typeId;
            GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                    .search(key,
                            GeoReference.fromCoordinate(longitude, latitude),
                            new Distance(radius, RedisGeoCommands.DistanceUnit.METERS),
                            RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs()
                                    .includeDistance()
                                    .includeCoordinates()
                                    .sortAscending());

            if (results != null) {
                for (GeoResult<RedisGeoCommands.GeoLocation<String>> geoResult : results.getContent()) {
                    Map<String, Object> shopMap = buildShopMap(geoResult);
                    if (shopMap != null) {
                        result.add(shopMap);
                    }
                }
            }
        } else {
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
                                                .sortAscending());

                        if (results != null) {
                            for (GeoResult<RedisGeoCommands.GeoLocation<String>> geoResult : results.getContent()) {
                                Map<String, Object> shopMap = buildShopMap(geoResult);
                                if (shopMap != null && !containsShop(result, (Long) shopMap.get("id"))) {
                                    result.add(shopMap);
                                }
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        result.sort(Comparator.comparingDouble(s -> (Double) s.get("distance")));
        return Result.ok(result);
    }

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
        result.put("cityCode", shop.getCityCode());
        result.put("province", shop.getProvince());
        result.put("city", shop.getCity());
        result.put("district", shop.getDistrict());
        result.put("adcode", shop.getAdcode());
        result.put("area", shop.getArea());
        return Result.ok(result);
    }

    @GetMapping("/search")
    public Result searchShopsOnMap(@RequestParam("keyword") String keyword,
                                   @RequestParam("longitude") Double longitude,
                                   @RequestParam("latitude") Double latitude,
                                   @RequestParam(value = "radius", defaultValue = "10000") Double radius) {
        List<Shop> shops = shopService.lambdaQuery()
                .like(Shop::getName, keyword)
                .or()
                .like(Shop::getAddress, keyword)
                .list();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Shop shop : shops) {
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
                shopMap.put("cityCode", shop.getCityCode());
                shopMap.put("province", shop.getProvince());
                shopMap.put("city", shop.getCity());
                shopMap.put("district", shop.getDistrict());
                shopMap.put("adcode", shop.getAdcode());
                shopMap.put("area", shop.getArea());
                result.add(shopMap);
            }
        }

        result.sort(Comparator.comparingDouble(s -> (Double) s.get("distance")));
        return Result.ok(result);
    }

    private double calculateDistance(double lng1, double lat1, double lng2, double lat2) {
        double radLat1 = Math.toRadians(lat1);
        double radLat2 = Math.toRadians(lat2);
        double a = radLat1 - radLat2;
        double b = Math.toRadians(lng1) - Math.toRadians(lng2);
        double s = 2 * Math.asin(Math.sqrt(Math.pow(Math.sin(a / 2), 2) +
                Math.cos(radLat1) * Math.cos(radLat2) * Math.pow(Math.sin(b / 2), 2)));
        return s * 6378137;
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
            shopMap.put("cityCode", shop.getCityCode());
            shopMap.put("province", shop.getProvince());
            shopMap.put("city", shop.getCity());
            shopMap.put("district", shop.getDistrict());
            shopMap.put("adcode", shop.getAdcode());
            shopMap.put("area", shop.getArea());
            return shopMap;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean containsShop(List<Map<String, Object>> list, Long shopId) {
        return list.stream().anyMatch(item -> shopId.equals(item.get("id")));
    }

    private LocationContextDTO enrichLocationContext(LocationContextDTO context) {
        if (context == null) {
            context = new LocationContextDTO();
            context.setProvider("amap");
            context.setAmapAvailable(false);
            context.setConfidence("none");
        }
        CityProfileDTO profile = cityService.matchCityProfile(context.getCityCode(), context.getCity());
        boolean enabled = profile != null && Boolean.TRUE.equals(profile.getOpen());
        context.setCityEditionEnabled(enabled);
        context.setCityProfile(enabled ? profile : null);
        return context;
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String[] headers = {"X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP"};
        for (String header : headers) {
            String value = request.getHeader(header);
            if (StrUtil.isNotBlank(value) && !"unknown".equalsIgnoreCase(value)) {
                return value.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    private JSONObject callAmap(String url, Map<String, Object> params) {
        String response = HttpUtil.get(url, params);
        JSONObject jsonObject = JSONUtil.parseObj(response);
        if (!"1".equals(jsonObject.getStr("status"))) {
            throw new IllegalStateException(StrUtil.blankToDefault(jsonObject.getStr("info"), "AMap request failed"));
        }
        return jsonObject;
    }
}
