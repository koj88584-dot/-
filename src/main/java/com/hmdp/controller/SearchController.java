package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Shop;
import com.hmdp.service.IAmapService;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IShopService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.*;

/**
 * 搜索控制器
 */
@Slf4j
@RestController
@RequestMapping("/shop-search")
public class SearchController {

    @Resource
    private IShopService shopService;

    @Resource
    private IBlogService blogService;

    @Resource
    private IAmapService amapService;

    /**
     * 综合搜索（店铺+博客）
     */
    @PostMapping("")
    public Result search(@RequestBody Map<String, Object> request) {
        String keyword = (String) request.get("keyword");
        String type = (String) request.getOrDefault("type", "all");
        String sortBy = (String) request.getOrDefault("sortBy", "relevance");
        Integer current = (Integer) request.getOrDefault("current", 1);
        Double longitude = (Double) request.get("longitude");
        Double latitude = (Double) request.get("latitude");
        
        log.info("搜索请求 - keyword: {}, type: {}, sortBy: {}, current: {}, longitude: {}, latitude: {}",
                keyword, type, sortBy, current, longitude, latitude);

        Map<String, Object> result = new HashMap<>();
        List<Shop> shops = new ArrayList<>();
        List<Blog> blogs = new ArrayList<>();

        // 搜索店铺
        if ("all".equals(type) || "shop".equals(type)) {
            shops = searchShops(keyword, sortBy, longitude, latitude);
        }

        // 搜索博客
        if ("all".equals(type) || "blog".equals(type)) {
            blogs = searchBlogs(keyword);
        }

        result.put("shops", shops);
        result.put("blogs", blogs);
        result.put("total", shops.size() + blogs.size());

        return Result.ok(result);
    }

    /**
     * 搜索店铺
     */
    private List<Shop> searchShops(String keyword, String sortBy, Double longitude, Double latitude) {
        List<Shop> shops = new ArrayList<>();

        // 如果有位置信息，优先从高德地图搜索
        if (longitude != null && latitude != null) {
            log.info("直接从高德地图搜索关键词: {}, 位置: {}, {}", keyword, longitude, latitude);
            try {
                if (amapService instanceof com.hmdp.service.impl.AmapServiceImpl) {
                    com.hmdp.service.impl.AmapServiceImpl amapServiceImpl = (com.hmdp.service.impl.AmapServiceImpl) amapService;
                    shops = amapServiceImpl.searchNearbyShops(null, keyword, longitude, latitude, 5000);
                }
            } catch (Exception e) {
                log.error("调用高德地图API异常", e);
            }
        } else {
            // 没有位置信息时，搜索本地数据库
            log.info("从本地数据库搜索关键词: {}", keyword);
            shops = shopService.lambdaQuery()
                    .like(Shop::getName, keyword)
                    .or()
                    .like(Shop::getAddress, keyword)
                    .last("LIMIT 20")  // 限制返回数量
                    .list();
        }

        // 计算距离
        if (longitude != null && latitude != null) {
            for (Shop shop : shops) {
                if (shop.getX() != null && shop.getY() != null) {
                    double distance = calculateDistance(longitude, latitude, shop.getX(), shop.getY());
                    shop.setDistance(distance);
                }
            }
        }

        // 排序
        switch (sortBy) {
            case "distance":
                // 按距离排序
                if (longitude != null && latitude != null) {
                    shops.sort(Comparator.comparingDouble(s -> s.getDistance() != null ? s.getDistance() : Double.MAX_VALUE));
                }
                break;
            case "rating":
                // 按评分排序
                shops.sort((s1, s2) -> {
                    int score1 = s1.getScore() != null ? s1.getScore() : 0;
                    int score2 = s2.getScore() != null ? s2.getScore() : 0;
                    return Integer.compare(score2, score1);
                });
                break;
            case "relevance":
            default:
                // 按相关度排序（综合名称匹配度、评分、销量）
                shops.sort((s1, s2) -> {
                    double score1 = calculateRelevanceScore(s1, keyword);
                    double score2 = calculateRelevanceScore(s2, keyword);
                    return Double.compare(score2, score1);  // 降序排列
                });
                break;
        }

        return shops;
    }

    /**
     * 计算相关度得分
     * 权重：名称匹配(40%) + 评分(30%) + 销量(20%) + 评论数(10%)
     */
    private double calculateRelevanceScore(Shop shop, String keyword) {
        double score = 0;
        String name = shop.getName() != null ? shop.getName() : "";

        // 名称匹配度（最高50分）
        if (name.contains(keyword)) {
            score += 40;
            if (name.startsWith(keyword)) {
                score += 10; // 前缀匹配额外加分
            }
        }

        // 评分权重（0-30分）
        if (shop.getScore() != null) {
            score += (shop.getScore() / 50.0) * 30;
        }

        // 销量权重（0-20分）
        if (shop.getSold() != null) {
            score += Math.min(shop.getSold() / 1000.0, 1.0) * 20;
        }

        // 评论数权重（0-10分）
        if (shop.getComments() != null) {
            score += Math.min(shop.getComments() / 500.0, 1.0) * 10;
        }

        return score;
    }

    /**
     * 搜索博客
     */
    private List<Blog> searchBlogs(String keyword) {
        return blogService.lambdaQuery()
                .like(Blog::getTitle, keyword)
                .or()
                .like(Blog::getContent, keyword)
                .orderByDesc(Blog::getCreateTime)
                .list();
    }

    /**
     * 获取热门搜索
     */
    @GetMapping("/hot")
    public Result getHotSearches() {
        // 返回预设的热门搜索
        List<Map<String, Object>> hotSearches = Arrays.asList(
                createHotSearch("美食", 1),
                createHotSearch("KTV", 2),
                createHotSearch("火锅", 3),
                createHotSearch("按摩", 4),
                createHotSearch("健身", 5),
                createHotSearch("烧烤", 6),
                createHotSearch("酒吧", 7),
                createHotSearch("美容", 8)
        );
        return Result.ok(hotSearches);
    }

    private Map<String, Object> createHotSearch(String keyword, int rank) {
        Map<String, Object> map = new HashMap<>();
        map.put("keyword", keyword);
        map.put("rank", rank);
        return map;
    }

    /**
     * 获取搜索历史
     */
    @GetMapping("/history")
    public Result getSearchHistory() {
        // 实际项目中应该从Redis或数据库获取用户的搜索历史
        // 这里返回空列表
        return Result.ok(new ArrayList<>());
    }

    /**
     * 记录搜索历史
     */
    @PostMapping("/history")
    public Result recordSearchHistory(@RequestParam("keyword") String keyword) {
        // 实际项目中应该保存到Redis或数据库
        log.info("记录搜索历史: {}", keyword);
        return Result.ok();
    }

    /**
     * 清空搜索历史
     */
    @DeleteMapping("/history")
    public Result clearSearchHistory() {
        // 实际项目中应该清除Redis或数据库中的记录
        log.info("清空搜索历史");
        return Result.ok();
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
}
