package com.hmdp.service.impl;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.config.AmapConfig;
import com.hmdp.entity.Shop;
import com.hmdp.service.IAmapService;
import com.hmdp.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 高德地图服务实现类
 */
@Slf4j
@Service
public class AmapServiceImpl implements IAmapService {

    @Resource
    private AmapConfig amapConfig;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 高德地图POI搜索API
    private static final String AMAP_POI_SEARCH_URL = "https://restapi.amap.com/v3/place/around";

    // 店铺类型与高德POI类型的映射（根据前端实际的typeId）
    // 前端typeId: 1=美食, 2=KTV, 3=丽人/美发, 4=健身运动, 5=按摩/足疗, 6=美容SPA, 7=亲子游乐, 8=酒吧, 9=轰趴馆, 10=美睫美甲
    // 高德地图POI分类编码来源: https://lbs.amap.com/api/webservice/guide/api/search
    private static final String[] TYPE_MAPPING = {
            "050000",   // 0-默认美食
            "050000",   // 1-美食 (050000-餐饮相关)
            "080302",   // 2-KTV (080302-KTV)
            "071100",   // 3-丽人/美发 (071100-美容美发店)
            "080111",   // 4-健身运动 (080111-健身中心)
            "071400",   // 5-按摩/足疗 (071400-洗浴推拿场所)
            "071100",   // 6-美容SPA (071100-美容美发店)
            "080501",   // 7-亲子游乐 (080501-游乐场)
            "080304",   // 8-酒吧 (080304-酒吧)
            "080501",   // 9-轰趴馆 -> 使用游乐场(080501-游乐场)
            "071100"    // 10-美睫美甲 (071100-美容美发店)
    };

    @Override
    public List<Shop> searchNearbyShops(Integer typeId, Double x, Double y, Integer radius) {
        return searchNearbyShops(typeId, null, x, y, radius);
    }

    @Override
    public List<Shop> searchNearbyShops(Integer typeId, String keyword, Double x, Double y, Integer radius) {
        log.info("开始搜索高德地图周边店铺 - typeId: {}, keyword: {}, x: {}, y: {}, radius: {}", typeId, keyword, x, y, radius);

        List<Shop> shops = new ArrayList<>();

        try {
            // 构建请求参数
            String location = x + "," + y;
            String keywords = keyword != null ? keyword : getKeywordsByType(typeId);
            String types = getAmapTypeByTypeId(typeId);

            // 构建URL
            StringBuilder urlBuilder = new StringBuilder(AMAP_POI_SEARCH_URL);
            urlBuilder.append("?key=").append(amapConfig.getKey())
                    .append("&location=").append(location)
                    .append("&radius=").append(radius)
                    .append("&keywords=").append(keywords)
                    .append("&offset=20")
                    .append("&page=1")
                    .append("&extensions=all")
                    .append("&output=JSON");
            
            // 只有当types不为空时才添加types参数
            if (types != null && !types.isEmpty()) {
                urlBuilder.append("&types=").append(types);
            }
            
            String url = urlBuilder.toString();

            log.debug("高德地图请求URL: {}", url);

            // 发送请求
            String response = HttpUtil.get(url);
            log.debug("高德地图响应: {}", response);

            // 解析响应
            JSONObject jsonObject = JSONUtil.parseObj(response);
            String status = jsonObject.getStr("status");

            if (!"1".equals(status)) {
                String info = jsonObject.getStr("info");
                log.error("高德地图API调用失败: {}", info);
                return shops;
            }

            // 解析POI列表
            JSONArray pois = jsonObject.getJSONArray("pois");
            if (pois == null || pois.isEmpty()) {
                log.info("高德地图未找到周边店铺");
                return shops;
            }

            log.info("高德地图返回 {} 个POI", pois.size());

            for (int i = 0; i < pois.size(); i++) {
                JSONObject poi = pois.getJSONObject(i);
                Shop shop = convertPoiToShop(poi, typeId);
                if (shop != null) {
                    shops.add(shop);
                    // 搜索时缓存，但TTL较短（5分钟），用于详情页查询
                    cacheShopToRedis(shop);
                }
            }

        } catch (Exception e) {
            log.error("调用高德地图API异常", e);
        }

        log.info("成功转换 {} 个店铺", shops.size());
        return shops;
    }

    /**
     * 将高德POI转换为店铺实体
     */
    private Shop convertPoiToShop(JSONObject poi, Integer typeId) {
        try {
            Shop shop = new Shop();

            // 使用POI的ID作为店铺ID（需要处理，避免与现有数据冲突）
            String poiId = poi.getStr("id");
            // 生成一个基于POI ID的正数ID（在Long范围内）
            // 使用hashCode并确保为正数，加上一个大基数避免与本地数据冲突
            long hash = Math.abs((long) poiId.hashCode());
            shop.setId(9000000000L + hash % 1000000000L); // 9开头，10位数，避免与本地数据冲突

            shop.setName(poi.getStr("name"));
            shop.setTypeId(typeId != null ? Long.valueOf(typeId) : 1L); // 默认为美食类型
            shop.setAddress(poi.getStr("address"));
            shop.setArea(poi.getStr("adname")); // 区域名称

            // 解析坐标
            String location = poi.getStr("location");
            if (location != null && location.contains(",")) {
                String[] coords = location.split(",");
                shop.setX(Double.valueOf(coords[0]));
                shop.setY(Double.valueOf(coords[1]));
            }

            // 解析图片（取第一张）
            JSONArray photos = poi.getJSONArray("photos");
            if (photos != null && !photos.isEmpty()) {
                StringBuilder images = new StringBuilder();
                for (int i = 0; i < Math.min(photos.size(), 3); i++) {
                    JSONObject photo = photos.getJSONObject(i);
                    String url = photo.getStr("url");
                    if (url != null) {
                        if (images.length() > 0) {
                            images.append(",");
                        }
                        images.append(url);
                    }
                }
                shop.setImages(images.toString());
            } else {
                // 使用默认图片
                shop.setImages("/imgs/shop/default.png");
            }

            // 解析评分（高德返回的是0-5分，转换为0-50分）
            String bizExt = poi.getStr("biz_ext");
            if (bizExt != null) {
                JSONObject bizExtObj = JSONUtil.parseObj(bizExt);
                String rating = bizExtObj.getStr("rating");
                if (rating != null && !rating.isEmpty()) {
                    try {
                        double score = Double.parseDouble(rating) * 10;
                        shop.setScore((int) score);
                    } catch (NumberFormatException e) {
                        shop.setScore(40); // 默认4分
                    }
                } else {
                    shop.setScore(40);
                }
            } else {
                shop.setScore(40);
            }

            // 解析电话
            String tel = poi.getStr("tel");
            if (tel != null && !tel.isEmpty()) {
                shop.setPhone(tel.split(";")[0]); // 取第一个电话
            } else {
                shop.setPhone("");
            }

            // 解析营业时间
            String businessTime = poi.getStr("business_time");
            if (businessTime != null && !businessTime.isEmpty()) {
                shop.setOpenHours(businessTime);
            } else {
                shop.setOpenHours("09:00-22:00");
            }

            // 解析均价
            String cost = poi.getStr("cost");
            if (cost != null && !cost.isEmpty()) {
                try {
                    shop.setAvgPrice(Long.valueOf(cost));
                } catch (NumberFormatException e) {
                    shop.setAvgPrice(null);
                }
            } else {
                shop.setAvgPrice(null);
            }

            // 评论数
            String commentNum = poi.getStr("comment_num");
            if (commentNum != null && !commentNum.isEmpty()) {
                try {
                    shop.setComments(Integer.parseInt(commentNum));
                } catch (NumberFormatException e) {
                    shop.setComments((int) (Math.random() * 500) + 10);
                }
            } else {
                shop.setComments((int) (Math.random() * 500) + 10);
            }

            return shop;

        } catch (Exception e) {
            log.error("转换POI到Shop异常: {}", poi, e);
            return null;
        }
    }

    /**
     * 根据类型ID获取搜索关键词
     */
    private String getKeywordsByType(Integer typeId) {
        if (typeId == null) {
            return "";
        }
        switch (typeId) {
            case 1:
                return "美食";
            case 2:
                return "KTV";
            case 3:
                return "美发";
            case 4:
                return "健身";
            case 5:
                return "按摩";
            case 6:
                return "美容";
            case 7:
                return "亲子";
            case 8:
                return "酒吧";
            case 9:
                return "轰趴";
            case 10:
                return "美甲";
            default:
                return "";
        }
    }

    /**
     * 根据类型ID获取高德POI类型编码
     */
    private String getAmapTypeByTypeId(Integer typeId) {
        if (typeId == null || typeId < 0 || typeId >= TYPE_MAPPING.length) {
            return ""; // typeId为null时返回空字符串，表示不限制类型
        }
        return TYPE_MAPPING[typeId];
    }

    @Override
    public Shop convertToShop(Object amapData, Integer typeId) {
        if (amapData instanceof JSONObject) {
            return convertPoiToShop((JSONObject) amapData, typeId);
        }
        return null;
    }

    /**
     * 将高德地图店铺缓存到Redis（未收藏的店铺，使用短TTL 5分钟）
     */
    private void cacheShopToRedis(Shop shop) {
        try {
            if (shop != null && shop.getId() != null) {
                String shopJson = JSONUtil.toJsonStr(shop);

                // 未收藏的店铺：只写缓存，不加入本地数据库，TTL设置短一点（5分钟）
                // 统一使用 cache:shop 前缀，避免重复缓存
                String shopKey = RedisConstants.CACHE_SHOP_KEY + shop.getId();
                stringRedisTemplate.opsForValue().set(shopKey, shopJson, 5, TimeUnit.MINUTES);

                log.debug("缓存未收藏店铺到Redis（TTL 5分钟）: {}", shop.getId());
            }
        } catch (Exception e) {
            log.error("缓存店铺到Redis失败", e);
        }
    }
}
