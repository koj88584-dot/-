package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.config.AmapConfig;
import com.hmdp.dto.AmapSearchResultDTO;
import com.hmdp.dto.LocationContextDTO;
import com.hmdp.entity.Shop;
import com.hmdp.service.IAmapService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AmapServiceImpl implements IAmapService {

    private static final String AMAP_TEXT_SEARCH_URL = "https://restapi.amap.com/v3/place/text";
    private static final String AMAP_REVERSE_GEOCODE_URL = "https://restapi.amap.com/v3/geocode/regeo";
    private static final String AMAP_IP_LOCATION_URL = "https://restapi.amap.com/v3/ip";

    @Resource
    private AmapConfig amapConfig;

    @Override
    public List<Shop> searchNearbyShops(Integer typeId, Double x, Double y, Integer radius) {
        return searchNearbyShopsWithMeta(typeId, null, x, y, radius, 1, 20).getShops();
    }

    @Override
    public List<Shop> searchNearbyShops(Integer typeId, String keyword, Double x, Double y, Integer radius) {
        return searchNearbyShopsWithMeta(typeId, keyword, x, y, radius, 1, 20).getShops();
    }

    @Override
    public List<Shop> searchNearbyShops(Integer typeId, String keyword, Double x, Double y, Integer radius,
                                        Integer page, Integer pageSize) {
        return searchNearbyShopsWithMeta(typeId, keyword, x, y, radius, page, pageSize).getShops();
    }

    @Override
    public AmapSearchResultDTO searchNearbyShopsWithMeta(Integer typeId, String keyword, Double x, Double y,
                                                         Integer radius, Integer page, Integer pageSize) {
        AmapNearbySearchSupport.NearbySearchRequest request = AmapNearbySearchSupport.buildRequest(
                amapConfig.getKey(), typeId, keyword, x, y, radius, page, pageSize
        );
        if (request.isMissingKey()) {
            log.warn("AMap nearby search skipped, reason=missing_key, {}",
                    AmapNearbySearchSupport.requestSummary(request));
            return AmapSearchResultDTO.empty(request.getPage(), request.getPageSize());
        }
        return executeSearchRequest(
                request.getUrl(),
                typeId,
                request.getPage(),
                request.getPageSize(),
                AmapNearbySearchSupport.requestSummary(request)
        );
    }

    @Override
    public AmapSearchResultDTO searchTextShopsWithMeta(Integer typeId, String keyword, Double x, Double y,
                                                       Integer page, Integer pageSize) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safePageSize = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 25);
        String safeKeyword = StrUtil.isNotBlank(keyword)
                ? keyword.trim()
                : AmapNearbySearchSupport.getKeywordsByType(typeId);
        AmapSearchResultDTO empty = AmapSearchResultDTO.empty(safePage, safePageSize);
        if (StrUtil.isBlank(amapConfig.getKey()) || StrUtil.isBlank(safeKeyword)) {
            log.warn("AMap text search skipped, reason={}, keyword={}, typeId={}",
                    StrUtil.isBlank(amapConfig.getKey()) ? "missing_key" : "blank_keyword",
                    safeKeyword,
                    typeId);
            return empty;
        }

        StringBuilder urlBuilder = new StringBuilder(AMAP_TEXT_SEARCH_URL);
        urlBuilder.append("?key=").append(amapConfig.getKey())
                .append("&keywords=").append(URLEncoder.encode(safeKeyword, StandardCharsets.UTF_8))
                .append("&offset=").append(safePageSize)
                .append("&page=").append(safePage)
                .append("&extensions=all")
                .append("&output=JSON");

        String types = AmapNearbySearchSupport.getAmapTypeByTypeId(typeId);
        if (StrUtil.isNotBlank(types)) {
            urlBuilder.append("&types=").append(types);
        }
        if (x != null && y != null) {
            urlBuilder.append("&location=").append(x).append(",").append(y)
                    .append("&sortrule=distance");
        }

        String summary = "typeId=" + typeId + ", keyword=" + safeKeyword + ", page=" + safePage
                + ", pageSize=" + safePageSize + ", mode=text";
        return executeSearchRequest(urlBuilder.toString(), typeId, safePage, safePageSize, summary);
    }

    @Override
    public LocationContextDTO reverseGeocode(Double longitude, Double latitude, Integer accuracy, String source) {
        LocationContextDTO empty = emptyLocation(source, "none");
        empty.setLongitude(longitude);
        empty.setLatitude(latitude);
        empty.setAccuracy(accuracy);
        if (longitude == null || latitude == null || StrUtil.isBlank(amapConfig.getKey())) {
            return empty;
        }

        Map<String, Object> params = new HashMap<>();
        params.put("key", amapConfig.getKey());
        params.put("location", longitude + "," + latitude);
        params.put("extensions", "all");
        params.put("output", "JSON");

        try {
            JSONObject jsonObject = callAmap(AMAP_REVERSE_GEOCODE_URL, params);
            JSONObject regeocode = jsonObject.getJSONObject("regeocode");
            if (regeocode == null) {
                return empty;
            }
            return buildRegeoLocation(regeocode, longitude, latitude, accuracy, source);
        } catch (Exception e) {
            log.warn("AMap reverse geocode failed, longitude={}, latitude={}", longitude, latitude, e);
            return empty;
        }
    }

    @Override
    public LocationContextDTO locateByIp(String ip) {
        LocationContextDTO empty = emptyLocation("ip", "low");
        if (StrUtil.isBlank(amapConfig.getKey())) {
            return empty;
        }

        Map<String, Object> params = new HashMap<>();
        params.put("key", amapConfig.getKey());
        params.put("output", "JSON");
        String publicIp = normalizePublicIp(ip);
        if (StrUtil.isNotBlank(publicIp)) {
            params.put("ip", publicIp);
        }

        try {
            JSONObject jsonObject = callAmap(AMAP_IP_LOCATION_URL, params);
            LocationContextDTO context = emptyLocation("ip", "low");
            String province = cleanAmapText(jsonObject.getStr("province"));
            String city = cleanAmapText(jsonObject.getStr("city"));
            String adcode = cleanAmapText(jsonObject.getStr("adcode"));
            context.setAmapAvailable(true);
            context.setProvince(province);
            context.setCity(StrUtil.blankToDefault(city, province));
            context.setAdcode(adcode);
            context.setCityCode(normalizeCityCode(adcode));
            applyRectangleCenter(context, cleanAmapText(jsonObject.getStr("rectangle")));
            return context;
        } catch (Exception e) {
            log.warn("AMap IP location failed, ip={}", ip, e);
            return empty;
        }
    }

    @Override
    public Shop convertToShop(Object amapData, Integer typeId) {
        if (amapData instanceof JSONObject) {
            return convertPoiToShop((JSONObject) amapData, typeId);
        }
        return null;
    }

    private LocationContextDTO emptyLocation(String source, String confidence) {
        LocationContextDTO context = new LocationContextDTO();
        context.setSource(StrUtil.blankToDefault(source, "unknown"));
        context.setProvider("amap");
        context.setAmapAvailable(false);
        context.setConfidence(confidence);
        context.setCityEditionEnabled(false);
        return context;
    }

    private LocationContextDTO buildRegeoLocation(JSONObject regeocode, Double longitude, Double latitude,
                                                 Integer accuracy, String source) {
        LocationContextDTO context = emptyLocation(source, "high");
        context.setAmapAvailable(true);
        context.setLongitude(longitude);
        context.setLatitude(latitude);
        context.setAccuracy(accuracy);
        context.setFormattedAddress(cleanAmapText(regeocode.getStr("formatted_address")));

        JSONObject addressComponent = regeocode.getJSONObject("addressComponent");
        if (addressComponent != null) {
            String province = cleanAmapText(addressComponent.getStr("province"));
            String city = cleanAmapText(addressComponent.getStr("city"));
            String district = cleanAmapText(addressComponent.getStr("district"));
            String adcode = cleanAmapText(addressComponent.getStr("adcode"));
            context.setProvince(province);
            context.setCity(StrUtil.blankToDefault(city, province));
            context.setDistrict(district);
            context.setAdcode(adcode);
            context.setCityCode(normalizeCityCode(adcode));
        }
        return context;
    }

    private JSONObject callAmap(String url, Map<String, Object> params) {
        String response = HttpUtil.get(url, params);
        JSONObject jsonObject = JSONUtil.parseObj(response);
        if (!"1".equals(jsonObject.getStr("status"))) {
            throw new IllegalStateException(StrUtil.blankToDefault(jsonObject.getStr("info"), "AMap request failed"));
        }
        return jsonObject;
    }

    private String cleanAmapText(String value) {
        String text = StrUtil.blankToDefault(value, "").trim();
        if ("[]".equals(text) || "null".equalsIgnoreCase(text)) {
            return "";
        }
        return text;
    }

    private String normalizeCityCode(String adcode) {
        String code = cleanAmapText(adcode);
        if (code.length() >= 6) {
            return code.substring(0, 4) + "00";
        }
        return code;
    }

    private void applyRectangleCenter(LocationContextDTO context, String rectangle) {
        if (context == null || StrUtil.isBlank(rectangle) || !rectangle.contains(";")) {
            return;
        }
        try {
            String[] points = rectangle.split(";");
            String[] leftBottom = points[0].split(",");
            String[] rightTop = points[1].split(",");
            double lng = (Double.parseDouble(leftBottom[0]) + Double.parseDouble(rightTop[0])) / 2;
            double lat = (Double.parseDouble(leftBottom[1]) + Double.parseDouble(rightTop[1])) / 2;
            context.setLongitude(lng);
            context.setLatitude(lat);
        } catch (Exception ignored) {
        }
    }

    private String normalizePublicIp(String ip) {
        if (StrUtil.isBlank(ip)) {
            return "";
        }
        String value = ip.split(",")[0].trim();
        if ("127.0.0.1".equals(value) || "0:0:0:0:0:0:0:1".equals(value) || "localhost".equalsIgnoreCase(value)) {
            return "";
        }
        if (value.startsWith("10.") || value.startsWith("192.168.") || value.startsWith("172.")) {
            return "";
        }
        return value;
    }

    private AmapSearchResultDTO executeSearchRequest(String url, Integer typeId, Integer page, Integer pageSize,
                                                     String requestSummary) {
        AmapSearchResultDTO result = AmapSearchResultDTO.empty(page, pageSize);
        try {
            String response = HttpUtil.get(url);
            JSONObject jsonObject = JSONUtil.parseObj(response);
            String status = jsonObject.getStr("status");
            if (!"1".equals(status)) {
                log.warn("AMap search failed, reason=api_error, info={}, {}",
                        jsonObject.getStr("info"),
                        requestSummary);
                return result;
            }

            result.setSuccess(true);
            JSONArray pois = jsonObject.getJSONArray("pois");
            long totalHits = AmapNearbySearchSupport.parseTotalHits(
                    jsonObject.getStr("count"),
                    pois == null ? 0 : pois.size()
            );
            result.setTotalHits(totalHits);
            result.setHasMore(AmapNearbySearchSupport.hasMore(totalHits, page, pageSize));

            if (pois == null || pois.isEmpty()) {
                log.info("AMap search returned empty result, {}", requestSummary);
                return result;
            }

            List<Shop> shops = new ArrayList<>(pois.size());
            for (int i = 0; i < pois.size(); i++) {
                Shop shop = convertPoiToShop(pois.getJSONObject(i), typeId);
                if (shop == null) {
                    continue;
                }
                shops.add(shop);
            }

            result.setShops(shops);
            log.info("AMap search converted {} shops, {}", shops.size(), requestSummary);
            return result;
        } catch (Exception e) {
            log.error("AMap search failed, reason=exception, {}", requestSummary, e);
            return result;
        }
    }

    private Shop convertPoiToShop(JSONObject poi, Integer typeId) {
        try {
            String poiId = poi.getStr("id");
            if (StrUtil.isBlank(poiId)) {
                return null;
            }

            Shop shop = new Shop();
            shop.setAmapPoiId(poiId);
            long hash = Math.abs((long) poiId.hashCode());
            shop.setId(9_000_000_000L + hash % 1_000_000_000L);
            shop.setName(poi.getStr("name"));
            shop.setTypeId(typeId != null ? Long.valueOf(typeId) : 1L);
            shop.setAddress(poi.getStr("address"));
            shop.setArea(poi.getStr("adname"));
            shop.setProvince(poi.getStr("pname"));
            shop.setCity(StrUtil.blankToDefault(poi.getStr("cityname"), poi.getStr("pname")));
            shop.setDistrict(poi.getStr("adname"));
            shop.setAdcode(poi.getStr("adcode"));
            shop.setCityCode(poi.getStr("adcode"));

            String location = poi.getStr("location");
            if (StrUtil.isNotBlank(location) && location.contains(",")) {
                String[] coords = location.split(",");
                if (coords.length >= 2) {
                    shop.setX(Double.valueOf(coords[0]));
                    shop.setY(Double.valueOf(coords[1]));
                }
            }

            JSONArray photos = poi.getJSONArray("photos");
            if (photos != null && !photos.isEmpty()) {
                StringBuilder images = new StringBuilder();
                for (int i = 0; i < Math.min(photos.size(), 3); i++) {
                    JSONObject photo = photos.getJSONObject(i);
                    if (photo == null) {
                        continue;
                    }
                    String photoUrl = photo.getStr("url");
                    if (StrUtil.isBlank(photoUrl)) {
                        continue;
                    }
                    if (images.length() > 0) {
                        images.append(",");
                    }
                    images.append(photoUrl);
                }
                shop.setImages(images.length() > 0 ? images.toString() : "/imgs/shop/default.png");
            } else {
                shop.setImages("/imgs/shop/default.png");
            }

            JSONObject bizExt = poi.getJSONObject("biz_ext");
            if (bizExt == null && StrUtil.isNotBlank(poi.getStr("biz_ext"))) {
                bizExt = JSONUtil.parseObj(poi.getStr("biz_ext"));
            }
            if (bizExt != null && StrUtil.isNotBlank(bizExt.getStr("rating"))) {
                try {
                    shop.setScore((int) (Double.parseDouble(bizExt.getStr("rating")) * 10));
                } catch (NumberFormatException e) {
                    shop.setScore(40);
                }
            } else {
                shop.setScore(40);
            }

            String tel = poi.getStr("tel");
            shop.setPhone(StrUtil.isBlank(tel) ? "" : tel.split(";")[0]);

            String businessTime = poi.getStr("business_time");
            shop.setOpenHours(StrUtil.isBlank(businessTime) ? "09:00-22:00" : businessTime);

            String cost = poi.getStr("cost");
            if (StrUtil.isNotBlank(cost)) {
                try {
                    shop.setAvgPrice(Long.valueOf(cost));
                } catch (NumberFormatException e) {
                    shop.setAvgPrice(null);
                }
            }

            shop.setSold(0);

            String commentNum = poi.getStr("comment_num");
            if (StrUtil.isNotBlank(commentNum)) {
                try {
                    shop.setComments(Integer.parseInt(commentNum));
                } catch (NumberFormatException e) {
                    shop.setComments(10);
                }
            } else {
                shop.setComments(10);
            }
            return shop;
        } catch (Exception e) {
            log.error("AMap POI convert failed: {}", poi, e);
            return null;
        }
    }
}
