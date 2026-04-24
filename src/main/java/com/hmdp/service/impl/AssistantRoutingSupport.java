package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.AssistantActionDTO;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class AssistantRoutingSupport {

    private static final String SEARCH_PATH = "/pages/misc/search.html";
    private static final String ORDER_PATH = "/pages/order/orders.html";
    private static final String VOUCHER_PATH = "/pages/misc/vouchers.html";
    private static final String BLOG_EDIT_PATH = "/pages/blog/blog-edit.html";

    private static final String[] SEARCH_HINTS = {
            "火锅", "烧烤", "烤肉", "咖啡", "奶茶", "KTV", "酒吧", "SPA", "按摩", "足疗",
            "美食", "甜品", "面包", "小吃", "湘菜", "粤菜", "川菜", "日料", "寿司", "牛排",
            "汉堡", "披萨", "海鲜", "夜宵", "餐厅", "饭店", "茶饮"
    };

    private static final List<String> SEARCH_STOP_WORDS = Arrays.asList(
            "帮我", "给我", "我想", "想找", "找一下", "找一个", "找个", "附近", "附近的", "周边",
            "离我近", "离我近的", "有没有", "有木有", "打开", "查看", "看看", "推荐", "一个",
            "顺便", "还能", "可以", "可用", "可领券", "优惠券", "领券", "券", "订单", "支付",
            "待支付", "已支付", "发一篇", "发个", "写个", "笔记", "探店", "高分", "评分高", "好评"
    );

    private AssistantRoutingSupport() {
    }

    static AssistantIntent analyzeIntent(String userMessage) {
        AssistantIntent intent = new AssistantIntent();
        intent.rawText = StrUtil.blankToDefault(userMessage, "").trim();
        intent.searchIntent = isSearchIntent(intent.rawText);
        intent.voucherIntent = containsAny(intent.rawText, "优惠券", "领券", "折扣", "满减", "秒杀", "抢券");
        intent.orderIntent = containsAny(intent.rawText, "订单", "支付", "退款", "取消", "核销");
        intent.blogIntent = containsAny(intent.rawText, "发笔记", "写笔记", "发探店", "发布笔记", "分享");
        intent.nearbyIntent = containsAny(intent.rawText, "附近", "周边", "离我近");
        intent.ratingIntent = containsAny(intent.rawText, "评分高", "高分", "好评", "评分好的");
        intent.keyword = extractSearchKeyword(intent.rawText);
        intent.sortBy = intent.ratingIntent ? "rating" : (intent.nearbyIntent ? "distance" : "relevance");

        if (containsAny(intent.rawText, "待支付", "没付款", "未支付")) {
            intent.orderStatus = "1";
        } else if (containsAny(intent.rawText, "已支付", "付款成功")) {
            intent.orderStatus = "2";
        } else {
            intent.orderStatus = "0";
        }
        return intent;
    }

    static List<AssistantActionDTO> buildActions(AssistantIntent intent) {
        List<AssistantActionDTO> actions = new ArrayList<>();

        if (intent.blogIntent) {
            actions.add(buildBlogAction(intent, true));
            actions.add(buildSearchAction(intent, false, "先去找店", "先找一个相关店铺，再把内容带回笔记里"));
            return actions;
        }

        if (intent.searchIntent && intent.voucherIntent) {
            actions.add(buildSearchAction(intent, true, buildSearchLabel(intent), "先带你去找店，也把优惠券入口一起准备好"));
            actions.add(buildVoucherAction("available", false, "查看优惠券", "看看当前有哪些可领优惠券"));
            return actions;
        }

        if (intent.orderIntent) {
            actions.add(buildOrderAction(intent.orderStatus, true, buildOrderLabel(intent.orderStatus), buildOrderDescription(intent.orderStatus)));
            actions.add(buildVoucherAction("my", false, "查看我的优惠券", "看看已领取的优惠券和券订单"));
            return actions;
        }

        if (intent.searchIntent) {
            actions.add(buildSearchAction(intent, true, buildSearchLabel(intent), "自动带着关键词去搜索页"));
            actions.add(buildVoucherAction("available", false, "顺便看看优惠券", "找店前先看看有没有可领的券"));
            return actions;
        }

        if (intent.voucherIntent) {
            actions.add(buildVoucherAction("available", true, "去领优惠券", "打开可领取优惠券页并切到可领取列表"));
            actions.add(buildOrderAction("0", false, "查看订单", "看看领券后产生的券订单状态"));
            return actions;
        }

        actions.add(buildSearchAction(intent, false, "去搜索页", "搜索店铺、地点和笔记"));
        actions.add(buildOrderAction("0", false, "查看订单", "继续处理你的订单"));
        actions.add(buildVoucherAction("available", false, "看看优惠券", "看看当前能领哪些券"));
        return actions;
    }

    static String buildRoutedReply(AssistantIntent intent, List<AssistantActionDTO> actions) {
        if (actions == null || actions.isEmpty()) {
            return "我先帮你整理常用入口，你也可以直接告诉我想找什么、想看哪个订单，或者想发什么笔记。";
        }

        AssistantActionDTO primary = actions.get(0);
        String type = StrUtil.blankToDefault(primary.getType(), "");
        Map<String, Object> params = primary.getParams();

        if ("search_shop".equals(type)) {
            String keyword = stringParam(params, "keyword");
            String sortBy = stringParam(params, "sortBy");
            boolean couponIntent = "true".equals(stringParam(params, "couponIntent"));
            if (couponIntent) {
                return "正在帮你找" + StrUtil.blankToDefault(keyword, "附近好店") + "，也把优惠券入口一起带上。";
            }
            if ("rating".equals(sortBy)) {
                return "正在帮你找评分更高的" + StrUtil.blankToDefault(keyword, "好店") + "，马上带你过去。";
            }
            if ("distance".equals(sortBy)) {
                return "正在帮你找附近的" + StrUtil.blankToDefault(keyword, "好店") + "，马上带你去搜索结果页。";
            }
            return "正在帮你找" + StrUtil.blankToDefault(keyword, "好店") + "，马上带你去搜索结果页。";
        }

        if ("open_orders".equals(type)) {
            String status = stringParam(params, "status");
            if ("1".equals(status)) {
                return "正在为你打开待支付订单，进去后就能继续付款或取消。";
            }
            if ("2".equals(status)) {
                return "正在为你打开已支付订单，方便你继续查看详情。";
            }
            return "正在为你打开订单页，你进去后就能继续处理订单。";
        }

        if ("open_vouchers".equals(type)) {
            String tab = stringParam(params, "tab");
            if ("my".equals(tab)) {
                return "正在为你打开我的优惠券，进去后可以直接看已领取的券。";
            }
            return "正在为你打开可领取优惠券，进去后可以直接挑券。";
        }

        if ("create_blog".equals(type)) {
            return "正在帮你打开发布笔记页，我会顺手把标题和草稿先给你填上。";
        }

        return "正在为你打开对应页面，你进去后就能继续操作。";
    }

    private static String buildSearchLabel(AssistantIntent intent) {
        String keyword = StrUtil.blankToDefault(intent.keyword, "好店");
        if (intent.nearbyIntent) {
            return "去找附近" + keyword;
        }
        return "去找" + keyword;
    }

    private static String buildOrderLabel(String status) {
        if ("1".equals(status)) {
            return "查看待支付订单";
        }
        if ("2".equals(status)) {
            return "查看已支付订单";
        }
        return "查看订单";
    }

    private static String buildOrderDescription(String status) {
        if ("1".equals(status)) {
            return "直接切到待支付订单";
        }
        if ("2".equals(status)) {
            return "直接切到已支付订单";
        }
        return "打开全部订单列表";
    }

    private static AssistantActionDTO buildSearchAction(AssistantIntent intent, boolean autoRun, String label, String description) {
        Map<String, Object> params = baseParams(autoRun);
        params.put("type", "shop");
        if ((intent.searchIntent || intent.blogIntent) && StrUtil.isNotBlank(intent.keyword)) {
            params.put("keyword", intent.keyword);
        }
        if (intent.searchIntent && StrUtil.isNotBlank(intent.sortBy) && !"relevance".equals(intent.sortBy)) {
            params.put("sortBy", intent.sortBy);
        }
        if (intent.searchIntent && intent.voucherIntent) {
            params.put("couponIntent", "true");
            params.put("couponTab", "available");
        }
        return action("search_shop", label, description, SEARCH_PATH, autoRun, params);
    }

    private static AssistantActionDTO buildOrderAction(String status, boolean autoRun, String label, String description) {
        Map<String, Object> params = baseParams(autoRun);
        params.put("status", StrUtil.blankToDefault(status, "0"));
        return action("open_orders", label, description, ORDER_PATH, autoRun, params);
    }

    private static AssistantActionDTO buildVoucherAction(String tab, boolean autoRun, String label, String description) {
        Map<String, Object> params = baseParams(autoRun);
        params.put("tab", StrUtil.blankToDefault(tab, "available"));
        return action("open_vouchers", label, description, VOUCHER_PATH, autoRun, params);
    }

    private static AssistantActionDTO buildBlogAction(AssistantIntent intent, boolean autoRun) {
        Map<String, Object> params = baseParams(autoRun);
        String keyword = extractSearchKeyword(intent.rawText);
        if (StrUtil.isNotBlank(keyword)) {
            params.put("shopKeyword", keyword);
            params.put("title", keyword + "探店记录");
            params.put("content", "今天想去看看" + keyword + "，先记下我的探店计划和想法。");
        } else {
            params.put("title", "新的探店笔记");
            params.put("content", "记录一下今天的探店体验、推荐菜和整体感受。");
        }
        return action("create_blog", "去发笔记", "打开笔记发布页并预填草稿", BLOG_EDIT_PATH, autoRun, params);
    }

    private static AssistantActionDTO action(String type, String label, String description, String basePath,
                                             boolean autoRun, Map<String, Object> params) {
        AssistantActionDTO dto = new AssistantActionDTO();
        dto.setType(type);
        dto.setLabel(label);
        dto.setDescription(description);
        dto.setAutoRun(autoRun);
        dto.setParams(params);
        dto.setPath(buildPath(basePath, params));
        return dto;
    }

    private static Map<String, Object> baseParams(boolean autoRun) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("source", "assistant");
        if (autoRun) {
            params.put("autoRun", "true");
        }
        return params;
    }

    private static String buildPath(String basePath, Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return basePath;
        }
        StringBuilder builder = new StringBuilder(basePath);
        builder.append(basePath.contains("?") ? "&" : "?");
        boolean first = true;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (entry.getValue() == null || StrUtil.isBlank(String.valueOf(entry.getValue()))) {
                continue;
            }
            if (!first) {
                builder.append("&");
            }
            builder.append(entry.getKey())
                    .append("=")
                    .append(urlEncode(String.valueOf(entry.getValue())));
            first = false;
        }
        return builder.toString();
    }

    private static boolean isSearchIntent(String text) {
        return containsAny(text, "想吃", "想喝", "附近", "找", "吃什么", "去哪", "店", "美食", "咖啡", "奶茶")
                || containsAny(text, SEARCH_HINTS);
    }

    private static String extractSearchKeyword(String text) {
        String value = StrUtil.blankToDefault(text, "").trim();
        if (StrUtil.isBlank(value)) {
            return "美食";
        }

        for (String hint : SEARCH_HINTS) {
            if (value.contains(hint)) {
                return hint;
            }
        }

        String cleaned = value
                .replaceAll("[+|,，。！？、/\\\\]", " ")
                .replaceAll("(帮我|给我|我想|想找|找一下|找一个|找个|附近的|周边的|离我近的|有没有|打开|查看|看看|推荐|顺便|还能|可以|可用|可领券|优惠券|领券|券|订单|支付|待支付|已支付|发一篇|发个|写个|笔记|探店|评分高的|高分的|好评的)", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (StrUtil.isBlank(cleaned)) {
            return "美食";
        }

        String bestToken = "";
        for (String rawToken : cleaned.split(" ")) {
            String token = trimKeywordToken(rawToken);
            if (StrUtil.isBlank(token) || token.length() < 2 || SEARCH_STOP_WORDS.contains(token)) {
                continue;
            }
            for (String hint : SEARCH_HINTS) {
                if (token.contains(hint)) {
                    return hint;
                }
            }
            if (token.length() > bestToken.length()) {
                bestToken = token;
            }
        }

        return StrUtil.blankToDefault(bestToken, "美食");
    }

    private static String trimKeywordToken(String token) {
        String value = StrUtil.blankToDefault(token, "").trim();
        if (value.endsWith("店铺")) {
            return value.substring(0, value.length() - 2);
        }
        if (value.endsWith("餐厅") || value.endsWith("商家")) {
            return value.substring(0, value.length() - 2);
        }
        if (value.length() > 2 && (value.endsWith("店") || value.endsWith("馆"))) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static String stringParam(Map<String, Object> params, String key) {
        if (params == null || !params.containsKey(key) || params.get(key) == null) {
            return "";
        }
        return String.valueOf(params.get(key));
    }

    private static boolean containsAny(String text, String... keywords) {
        String lowerCaseText = StrUtil.blankToDefault(text, "").toLowerCase();
        for (String keyword : keywords) {
            if (lowerCaseText.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    static final class AssistantIntent {
        private String rawText;
        private String keyword;
        private String sortBy;
        private String orderStatus;
        private boolean searchIntent;
        private boolean voucherIntent;
        private boolean orderIntent;
        private boolean blogIntent;
        private boolean nearbyIntent;
        private boolean ratingIntent;
    }
}
