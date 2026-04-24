package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;

final class AmapNearbySearchSupport {

    private static final String AMAP_POI_SEARCH_URL = "https://restapi.amap.com/v3/place/around";

    private static final String[] TYPE_MAPPING = {
            "050000",
            "050000",
            "080302",
            "071100",
            "080111",
            "071400",
            "071100",
            "080501",
            "080304",
            "080501",
            "071100"
    };

    private AmapNearbySearchSupport() {
    }

    static NearbySearchRequest buildRequest(String key, Integer typeId, String keyword, Double x, Double y,
                                            Integer radius, Integer page, Integer pageSize) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safePageSize = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 25);
        int safeRadius = radius == null ? 5000 : Math.min(radius, 50000);
        String safeKeyword = StrUtil.isNotBlank(keyword) ? keyword.trim() : getKeywordsByType(typeId);
        String types = getAmapTypeByTypeId(typeId);
        String location = x + "," + y;

        StringBuilder urlBuilder = new StringBuilder(AMAP_POI_SEARCH_URL);
        urlBuilder.append("?key=").append(StrUtil.blankToDefault(key, ""))
                .append("&location=").append(location)
                .append("&radius=").append(safeRadius)
                .append("&keywords=").append(safeKeyword)
                .append("&offset=").append(safePageSize)
                .append("&page=").append(safePage)
                .append("&extensions=all")
                .append("&output=JSON");
        if (StrUtil.isNotBlank(types)) {
            urlBuilder.append("&types=").append(types);
        }

        return new NearbySearchRequest(
                StrUtil.isBlank(key),
                typeId,
                location,
                safeKeyword,
                types,
                safeRadius,
                safePage,
                safePageSize,
                urlBuilder.toString()
        );
    }

    static Long parseTotalHits(String countText, int fallback) {
        if (StrUtil.isBlank(countText)) {
            return (long) fallback;
        }
        try {
            return Long.parseLong(countText);
        } catch (NumberFormatException e) {
            return (long) fallback;
        }
    }

    static boolean hasMore(long totalHits, int page, int pageSize) {
        return (long) page * pageSize < totalHits;
    }

    static String requestSummary(NearbySearchRequest request) {
        return "typeId=" + request.getTypeId()
                + ", keyword=" + request.getKeyword()
                + ", radius=" + request.getRadius()
                + ", page=" + request.getPage()
                + ", pageSize=" + request.getPageSize();
    }

    static String getKeywordsByType(Integer typeId) {
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
                return "购物";
            case 10:
                return "美甲";
            default:
                return "";
        }
    }

    static String getAmapTypeByTypeId(Integer typeId) {
        if (typeId == null || typeId < 0 || typeId >= TYPE_MAPPING.length) {
            return "";
        }
        return TYPE_MAPPING[typeId];
    }

    static final class NearbySearchRequest {
        private final boolean missingKey;
        private final Integer typeId;
        private final String location;
        private final String keyword;
        private final String types;
        private final int radius;
        private final int page;
        private final int pageSize;
        private final String url;

        private NearbySearchRequest(boolean missingKey, Integer typeId, String location, String keyword, String types,
                                    int radius, int page, int pageSize, String url) {
            this.missingKey = missingKey;
            this.typeId = typeId;
            this.location = location;
            this.keyword = keyword;
            this.types = types;
            this.radius = radius;
            this.page = page;
            this.pageSize = pageSize;
            this.url = url;
        }

        boolean isMissingKey() {
            return missingKey;
        }

        Integer getTypeId() {
            return typeId;
        }

        String getLocation() {
            return location;
        }

        String getKeyword() {
            return keyword;
        }

        String getTypes() {
            return types;
        }

        int getRadius() {
            return radius;
        }

        int getPage() {
            return page;
        }

        int getPageSize() {
            return pageSize;
        }

        String getUrl() {
            return url;
        }
    }
}
