package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.hmdp.entity.Shop;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ShopNearbySupport {

    private static final String DEFAULT_SHOP_IMAGE = "/imgs/shop/default.png";
    private static final String DEFAULT_SHOP_ADDRESS = "暂无地址";
    private static final String DEFAULT_OPEN_HOURS = "09:00-22:00";
    private static final int DEFAULT_SCORE = 40;

    private static final Comparator<Shop> DISTANCE_COMPARATOR =
            Comparator.comparing(shop -> shop.getDistance() == null ? Double.MAX_VALUE : shop.getDistance());

    private ShopNearbySupport() {
    }

    static Set<Long> collectSeenIds(Collection<Shop> shops) {
        Set<Long> seen = new LinkedHashSet<>();
        if (shops == null) {
            return seen;
        }
        for (Shop shop : shops) {
            if (shop == null || shop.getId() == null) {
                continue;
            }
            seen.add(shop.getId());
        }
        return seen;
    }

    static void mergeUnique(List<Shop> target, List<Shop> source, Set<Long> seen) {
        if (target == null || source == null || source.isEmpty() || seen == null) {
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

    static void sortByDistance(List<Shop> shops) {
        if (shops == null || shops.size() < 2) {
            return;
        }
        shops.sort(DISTANCE_COMPARATOR);
    }

    static List<Shop> page(List<Shop> shops, int page, int pageSize) {
        if (shops == null || shops.isEmpty() || pageSize <= 0) {
            return Collections.emptyList();
        }
        int safePage = Math.max(page, 1);
        int from = (safePage - 1) * pageSize;
        if (from >= shops.size()) {
            return Collections.emptyList();
        }
        int to = Math.min(from + pageSize, shops.size());
        return new ArrayList<>(shops.subList(from, to));
    }

    static List<Shop> deduplicateAndLimit(List<Shop> shops, int limit) {
        if (shops == null || shops.isEmpty() || limit <= 0) {
            return Collections.emptyList();
        }
        Map<Long, Shop> deduplicated = new LinkedHashMap<>();
        for (Shop shop : shops) {
            if (shop == null || shop.getId() == null) {
                continue;
            }
            deduplicated.putIfAbsent(shop.getId(), shop);
        }
        List<Shop> result = new ArrayList<>(deduplicated.values());
        sortByDistance(result);
        if (result.size() <= limit) {
            return result;
        }
        return new ArrayList<>(result.subList(0, limit));
    }

    static void fillDefaults(Shop shop) {
        if (shop == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        if (StrUtil.isBlank(shop.getImages())) {
            shop.setImages(DEFAULT_SHOP_IMAGE);
        }
        if (StrUtil.isBlank(shop.getAddress())) {
            shop.setAddress(DEFAULT_SHOP_ADDRESS);
        }
        if (shop.getSold() == null) {
            shop.setSold(0);
        }
        if (shop.getComments() == null) {
            shop.setComments(0);
        }
        if (shop.getScore() == null || shop.getScore() <= 0) {
            shop.setScore(DEFAULT_SCORE);
        }
        if (StrUtil.isBlank(shop.getOpenHours())) {
            shop.setOpenHours(DEFAULT_OPEN_HOURS);
        }
        if (shop.getCreateTime() == null) {
            shop.setCreateTime(now);
        }
        shop.setUpdateTime(now);
    }

    static void applyDistance(Shop shop, Double x, Double y) {
        if (shop == null || x == null || y == null || shop.getX() == null || shop.getY() == null) {
            return;
        }
        shop.setDistance(calculateDistance(x, y, shop.getX(), shop.getY()));
    }

    static double calculateDistance(double lng1, double lat1, double lng2, double lat2) {
        double radLat1 = Math.toRadians(lat1);
        double radLat2 = Math.toRadians(lat2);
        double a = radLat1 - radLat2;
        double b = Math.toRadians(lng1) - Math.toRadians(lng2);
        double s = 2 * Math.asin(Math.sqrt(Math.pow(Math.sin(a / 2), 2)
                + Math.cos(radLat1) * Math.cos(radLat2) * Math.pow(Math.sin(b / 2), 2)));
        return s * 6378137;
    }
}
