package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.DeepSeekSearchDTO;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class SearchKeywordSupport {

    private static final Set<String> SEARCH_STOP_WORDS = new HashSet<>(Arrays.asList(
            "帮我", "给我", "我想", "想找", "找下", "找一个", "找个", "附近", "附近的", "周边",
            "离我近", "离我近的", "有没有", "有木有", "来个", "打开", "查看", "看看", "顺便",
            "还能", "可以", "可用", "可领取", "优惠券", "领券", "券", "订单", "待支付", "已支付",
            "支付", "发一篇", "发个", "写个", "笔记", "探店", "高分", "评分高", "好评", "推荐",
            "一个", "一间", "一家", "店铺", "商家"
    ));

    private static final Map<String, SearchAliasRule> SEARCH_ALIAS_RULES = createAliasRules();

    private SearchKeywordSupport() {
    }

    static SearchQueryContext resolveSearchContext(DeepSeekSearchDTO dto) {
        String originalKeyword = StrUtil.trim(dto.getKeyword());
        String normalizedKeyword = normalizeKeyword(originalKeyword);
        List<String> extractedTokens = tokenizeKeyword(normalizedKeyword);
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        keywords.add(originalKeyword);
        keywords.addAll(extractedTokens);

        Long resolvedTypeId = dto.getTypeId();
        String amapKeyword = "";

        for (Map.Entry<String, SearchAliasRule> entry : SEARCH_ALIAS_RULES.entrySet()) {
            SearchAliasRule rule = entry.getValue();
            boolean matched = false;
            for (String alias : rule.aliases) {
                if (normalizedKeyword.contains(alias.toLowerCase())) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                continue;
            }

            keywords.add(entry.getKey());
            keywords.addAll(rule.aliases);
            if (resolvedTypeId == null) {
                resolvedTypeId = rule.typeId;
            }
            if (entry.getKey().length() >= amapKeyword.length()) {
                amapKeyword = entry.getKey();
            }
        }

        keywords.removeIf(item -> {
            String term = normalizeKeyword(item);
            return StrUtil.isBlank(term) || SEARCH_STOP_WORDS.contains(term);
        });

        if (keywords.isEmpty()) {
            keywords.add(originalKeyword);
        }

        if (StrUtil.isBlank(amapKeyword)) {
            for (String token : extractedTokens) {
                if (StrUtil.isBlank(token) || SEARCH_STOP_WORDS.contains(token)) {
                    continue;
                }
                if (token.length() >= amapKeyword.length()) {
                    amapKeyword = token;
                }
            }
        }

        if (StrUtil.isBlank(amapKeyword)) {
            amapKeyword = originalKeyword;
        }

        return new SearchQueryContext(
                originalKeyword,
                new ArrayList<>(keywords),
                resolvedTypeId,
                StrUtil.blankToDefault(amapKeyword, originalKeyword)
        );
    }

    private static String normalizeKeyword(String keyword) {
        return StrUtil.blankToDefault(keyword, "")
                .trim()
                .toLowerCase()
                .replace('，', ' ')
                .replace('+', ' ');
    }

    private static List<String> tokenizeKeyword(String keyword) {
        if (StrUtil.isBlank(keyword)) {
            return Collections.emptyList();
        }
        String cleaned = keyword
                .replaceAll("[,，。！？、|]", " ")
                .replaceAll("(附近的?|周边的?|离我近的?|评分高的?|高分的?|好评的?|优惠券|领券|可用|可领取)", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (StrUtil.isBlank(cleaned)) {
            return Collections.emptyList();
        }

        List<String> tokens = new ArrayList<>();
        for (String rawToken : cleaned.split(" ")) {
            String token = trimToken(rawToken);
            if (StrUtil.isBlank(token) || token.length() < 2 || SEARCH_STOP_WORDS.contains(token)) {
                continue;
            }
            tokens.add(token);
        }
        return tokens;
    }

    private static String trimToken(String token) {
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

    private static Map<String, SearchAliasRule> createAliasRules() {
        Map<String, SearchAliasRule> rules = new LinkedHashMap<>();
        registerRule(rules, 1L, "奶茶", "奶茶", "茶饮", "饮品", "奶盖", "冰厅", "茶餐厅");
        registerRule(rules, 1L, "咖啡", "咖啡", "咖啡馆", "咖啡店", "下午茶");
        registerRule(rules, 1L, "火锅", "火锅", "锅物", "串串", "铜锅", "海底捞");
        registerRule(rules, 1L, "烧烤", "烧烤", "烤肉", "烤串", "烤鱼");
        registerRule(rules, 1L, "甜品", "甜品", "蛋糕", "面包", "烘焙", "冰淇淋");
        registerRule(rules, 1L, "寿司", "寿司", "日料", "刺身");
        registerRule(rules, 1L, "牛排", "牛排", "西餐", "意面");
        registerRule(rules, 1L, "海鲜", "海鲜", "生蚝", "龙虾");
        registerRule(rules, 1L, "美食", "美食", "吃饭", "餐厅", "饭店", "小吃");
        registerRule(rules, 2L, "KTV", "ktv", "唱歌", "麦霸");
        registerRule(rules, 8L, "酒吧", "酒吧", "清吧", "小酒馆");
        registerRule(rules, 5L, "按摩", "按摩", "足疗", "推拿");
        registerRule(rules, 6L, "SPA", "spa", "美容", "护理");
        registerRule(rules, 4L, "健身", "健身", "运动", "瑜伽");
        return rules;
    }

    private static void registerRule(Map<String, SearchAliasRule> rules, Long typeId, String keyword, String... aliases) {
        List<String> allAliases = new ArrayList<>();
        allAliases.add(keyword.toLowerCase());
        for (String alias : aliases) {
            allAliases.add(alias.toLowerCase());
        }
        rules.put(keyword, new SearchAliasRule(typeId, allAliases));
    }
}

final class SearchAliasRule {
    final Long typeId;
    final List<String> aliases;

    SearchAliasRule(Long typeId, List<String> aliases) {
        this.typeId = typeId;
        this.aliases = aliases;
    }
}

final class SearchQueryContext {
    final String originalKeyword;
    final List<String> keywords;
    final Long resolvedTypeId;
    final String amapKeyword;

    SearchQueryContext(String originalKeyword, List<String> keywords, Long resolvedTypeId, String amapKeyword) {
        this.originalKeyword = originalKeyword;
        this.keywords = keywords;
        this.resolvedTypeId = resolvedTypeId;
        this.amapKeyword = amapKeyword;
    }
}
