package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.http.HtmlUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.CityHotNewsDTO;
import com.hmdp.dto.CityProfileDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.ICityHotNewsService;
import com.hmdp.service.ICityService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.annotation.Resource;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CityHotNewsServiceImpl implements ICityHotNewsService {

    private static final String CACHE_PREFIX = "city:hot-news:v9:";
    private static final String STALE_PREFIX = "city:hot-news:v9:stale:";
    private static final String ALAPI_SITE_CACHE_KEY = "city:hot-news:v9:alapi:sites";
    private static final String ARTICLE_ASSET_PREFIX = "city:hot-news:v9:asset:";
    private static final String GOOGLE_BATCH_EXECUTE_URL = "https://news.google.com/_/DotsSplashUi/data/batchexecute?rpcids=Fbv4je";
    private static final Pattern IMAGE_PATTERN = Pattern.compile("<img[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern META_TAG_PATTERN = Pattern.compile("<meta\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern LINK_TAG_PATTERN = Pattern.compile("<link\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTRIBUTE_PATTERN = Pattern.compile("([a-zA-Z_:][-a-zA-Z0-9_:.]*)\\s*=\\s*([\"'])(.*?)\\2", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern GOOGLE_NEWS_ID_PATTERN = Pattern.compile("data-n-a-id=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern GOOGLE_NEWS_TS_PATTERN = Pattern.compile("data-n-a-ts=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern GOOGLE_NEWS_SG_PATTERN = Pattern.compile("data-n-a-sg=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern GOOGLE_NEWS_RESOLVED_URL_PATTERN = Pattern.compile("\\[\\\\\"garturlres\\\\\",\\\\\"(.*?)\\\\\",");
    private static final int MIN_LOCAL_ITEMS = 6;

    @Resource
    private ICityService cityService;

    @Resource
    private ShopMapper shopMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Value("${hot-news.enabled:true}")
    private Boolean enabled;

    @Value("${hot-news.alapi.enabled:true}")
    private Boolean alapiEnabled;

    @Value("${hot-news.alapi.token:}")
    private String alapiToken;

    @Value("${hot-news.alapi.base-url:https://v3.alapi.cn}")
    private String alapiBaseUrl;

    @Value("${hot-news.alapi.weibo-path:/api/new/wbtop}")
    private String alapiWeiboPath;

    @Value("${hot-news.alapi.toutiao-path:/api/new/toutiao}")
    private String alapiToutiaoPath;

    @Value("${hot-news.alapi.zaobao-path:/api/zaobao}")
    private String alapiZaobaoPath;

    @Value("${hot-news.alapi.tophub-path:/api/tophub}")
    private String alapiTophubPath;

    @Value("${hot-news.alapi.tophub-site-path:/api/tophub/site}")
    private String alapiTophubSitePath;

    @Value("${hot-news.alapi.site-cache-hours:24}")
    private Long alapiSiteCacheHours;

    @Value("${hot-news.alapi.default-site-ids:BaXJOg,vaDrOK,gMJkaY,GMbZMo,yanZaZ,BOoYax}")
    private String alapiDefaultSiteIds;

    @Value("${hot-news.ttl-minutes:30}")
    private Long ttlMinutes;

    @Value("${hot-news.stale-hours:72}")
    private Long staleHours;

    @Value("${hot-news.max-items:20}")
    private Integer maxItems;

    @Value("${hot-news.google-news-rss-url:https://news.google.com/rss/search}")
    private String googleNewsRssUrl;

    @Value("${hot-news.allowed-hosts:news.google.com,bing.com,www.bing.com}")
    private String allowedHosts;

    @Value("${hot-news.max-age-days:14}")
    private Integer maxAgeDays;

    @Value("${hot-news.asset-cache-hours:12}")
    private Long assetCacheHours;

    @Override
    public Result queryHotNews(String cityCode, Integer current) {
        CityProfileDTO profile = cityService.getCityProfile(cityCode);
        String normalizedCityCode = profile == null || StrUtil.isBlank(profile.getCityCode()) ? cityCode : profile.getCityCode();
        String cacheKey = CACHE_PREFIX + normalizedCityCode + ":" + (current == null ? 1 : current);
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (StrUtil.isNotBlank(cached)) {
            return Result.ok(JSONUtil.parseObj(cached));
        }

        Map<String, Object> payload;
        String sourceFailureReason = null;
        if (Boolean.TRUE.equals(alapiEnabled)) {
            if (StrUtil.isBlank(alapiToken)) {
                sourceFailureReason = "ALAPI token 未配置";
            } else {
                try {
                    List<CityHotNewsDTO> news = crawlAlapiHotNews(profile, current);
                    if (!news.isEmpty()) {
                        hydrateHotNewsAssets(news);
                        payload = buildPayload(profile, news, true, false, "alapi-localized", null);
                        cachePayload(cacheKey, STALE_PREFIX + normalizedCityCode, payload);
                        return Result.ok(payload);
                    }
                    sourceFailureReason = "ALAPI 未返回足够的本地热点";
                } catch (Exception ignored) {
                    sourceFailureReason = "ALAPI 热点服务暂不可用";
                }
            }
        }
        if (Boolean.TRUE.equals(enabled)) {
            try {
                List<CityHotNewsDTO> news = crawlPublicRss(profile, current);
                if (!news.isEmpty()) {
                    hydrateHotNewsAssets(news);
                    payload = buildPayload(profile, news, true, false, "public-rss", null);
                    cachePayload(cacheKey, STALE_PREFIX + normalizedCityCode, payload);
                    return Result.ok(payload);
                }
            } catch (Exception ignored) {
                // Public RSS occasionally throttles. We intentionally fall back instead of blocking the page.
            }
        }

        String stale = stringRedisTemplate.opsForValue().get(STALE_PREFIX + normalizedCityCode);
        if (StrUtil.isNotBlank(stale)) {
            Map<String, Object> stalePayload = JSONUtil.parseObj(stale);
            stalePayload.put("stale", true);
            stalePayload.put("fallbackReason", StrUtil.isNotBlank(sourceFailureReason)
                    ? sourceFailureReason + "，展示最近一次成功抓取结果"
                    : "实时源暂不可用，展示最近一次真实抓取结果");
            stringRedisTemplate.opsForValue().set(cacheKey, JSONUtil.toJsonStr(stalePayload), Math.max(5, ttlMinutes), TimeUnit.MINUTES);
            return Result.ok(stalePayload);
        }

        payload = buildPayload(profile, Collections.emptyList(), false, true, "external-unavailable",
                StrUtil.isNotBlank(sourceFailureReason)
                        ? sourceFailureReason + "，当前城市暂未抓到足够的外部热点新闻"
                        : "当前城市暂未抓到足够的外部热点新闻");
        stringRedisTemplate.opsForValue().set(cacheKey, JSONUtil.toJsonStr(payload), Math.max(5, ttlMinutes), TimeUnit.MINUTES);
        return Result.ok(payload);
    }

    private List<CityHotNewsDTO> crawlAlapiHotNews(CityProfileDTO profile, Integer current) {
        LinkedHashMap<String, Integer> keywordWeights = buildLocalKeywordWeights(profile);
        Map<String, CityHotNewsDTO> deduped = new LinkedHashMap<>();
        try {
            collectCityPublicNews(deduped, profile, keywordWeights);
        } catch (Exception ignored) {
            // Location-first city news search is a strong signal, but failure should not block the hot-list pipeline.
        }
        try {
            collectAlapiWeiboHot(deduped, profile, keywordWeights);
        } catch (Exception ignored) {
            // Keep the pipeline alive even if a single ALAPI source is temporarily unavailable.
        }
        try {
            collectAlapiToutiaoHot(deduped, profile, keywordWeights);
        } catch (Exception ignored) {
            // Toutiao is a useful supplement, but it should never block Weibo-localized results.
        }
        try {
            collectAlapiMorningBriefing(deduped, profile, keywordWeights);
        } catch (Exception ignored) {
            // Morning briefing provides broad news coverage and can fail independently.
        }
        for (String siteId : resolveAlapiTophubSiteIds()) {
            try {
                collectAlapiTophub(deduped, profile, keywordWeights, siteId);
            } catch (Exception ignored) {
                // Some ALAPI tokens only have partial hot-list permissions. Skip denied boards and keep going.
            }
        }
        List<CityHotNewsDTO> ranked = rankLocalizedNews(deduped, profile, keywordWeights);
        if (ranked.isEmpty()) {
            return ranked;
        }
        int page = Math.max(current == null ? 1 : current, 1);
        int pageSize = Math.max(8, Math.min(maxItems == null ? 20 : maxItems, 30));
        int start = (page - 1) * pageSize;
        if (start >= ranked.size()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(ranked.subList(start, Math.min(ranked.size(), start + pageSize)));
    }

    private void collectCityPublicNews(Map<String, CityHotNewsDTO> deduped, CityProfileDTO profile,
                                       Map<String, Integer> keywordWeights) throws Exception {
        for (String query : buildCityNewsQueries(profile)) {
            String url = buildGoogleNewsSearchUrl(query);
            if (!isAllowedSourceUrl(url)) {
                continue;
            }
            String body = HttpRequest.get(url)
                    .timeout(5000)
                    .header("User-Agent", "HMDP-HotNewsBot/1.0 (+city-local news search)")
                    .execute()
                    .body();
            for (CityHotNewsDTO item : parseLocalizedRss(body, profile, query, keywordWeights)) {
                mergeHotItem(deduped, item);
            }
        }
    }

    private List<String> buildLegacyCityNewsQueries(CityProfileDTO profile) {
        String cityName = profile == null || StrUtil.isBlank(profile.getCityName()) ? "本地" : profile.getCityName();
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        queries.add(cityName + " 热点 新闻");
        queries.add(cityName + " 同城 热点");
        queries.add(cityName + " 今日 新闻");
        queries.add(cityName + " 本地 热门");
        if (profile != null) {
            if (profile.getFeaturedDistricts() != null) {
                for (String district : profile.getFeaturedDistricts()) {
                    if (queries.size() >= 6) {
                        break;
                    }
                    if (StrUtil.isNotBlank(district)) {
                        queries.add(cityName + " " + district + " 新闻");
                    }
                }
            }
            if (profile.getHotSearches() != null) {
                for (String keyword : profile.getHotSearches()) {
                    if (queries.size() >= 8) {
                        break;
                    }
                    if (StrUtil.isNotBlank(keyword)) {
                        queries.add(keyword + " 新闻");
                    }
                }
            }
        }
        return new ArrayList<>(queries);
    }

    private List<String> buildCityNewsQueries(CityProfileDTO profile) {
        String cityName = profile == null || StrUtil.isBlank(profile.getCityName()) ? "本地" : profile.getCityName();
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        queries.add(cityName + " 热点新闻 when:3d");
        queries.add(cityName + " 今日新闻 when:3d");
        queries.add(cityName + " 同城新闻 when:3d");
        queries.add(cityName + " 民生新闻 when:7d");
        queries.add(cityName + " 突发新闻 when:7d");
        if (profile != null && StrUtil.isNotBlank(profile.getProvince())) {
            queries.add(profile.getProvince() + " " + cityName + " 新闻 when:3d");
        }
        if (profile != null && profile.getFeaturedDistricts() != null) {
            for (String district : profile.getFeaturedDistricts()) {
                if (queries.size() >= 8) {
                    break;
                }
                if (StrUtil.isNotBlank(district)) {
                    queries.add(cityName + " " + district + " 新闻 when:7d");
                }
            }
        }
        return new ArrayList<>(queries);
    }

    private void collectAlapiWeiboHot(Map<String, CityHotNewsDTO> deduped, CityProfileDTO profile,
                                      Map<String, Integer> keywordWeights) {
        JSONObject response = postAlapi(alapiWeiboPath, new LinkedHashMap<String, Object>() {{
            put("num", "50");
        }});
        JSONArray data = response.getJSONArray("data");
        if (data == null) {
            return;
        }
        for (int i = 0; i < data.size(); i++) {
            JSONObject item = data.getJSONObject(i);
            if (item == null) {
                continue;
            }
            String title = StrUtil.trim(item.getStr("hot_word"));
            if (StrUtil.isBlank(title)) {
                continue;
            }
            String link = item.getStr("url");
            int rawHeat = parseInt(item.get("hot_word_num"), 0);
            List<String> matchedKeywords = extractMatchedKeywords(title, keywordWeights);
            if (!shouldKeepLocalizedCandidate(title, matchedKeywords, profile)) {
                continue;
            }
            int localizedHeat = computeLocalizedHeat(title, "微博热搜", rawHeat, i, matchedKeywords, keywordWeights, profile);
            mergeHotItem(
                    deduped,
                    localizedItem(
                            title,
                            "微博实时热搜，本地化关键词已加权筛选",
                            link,
                            "",
                            "微博热搜",
                            LocalDateTime.now(),
                            localizedHeat,
                            matchedKeywords,
                            true,
                            false
                    )
            );
        }
    }

    private void collectAlapiToutiaoHot(Map<String, CityHotNewsDTO> deduped, CityProfileDTO profile,
                                        Map<String, Integer> keywordWeights) {
        JSONObject response = postAlapi(alapiToutiaoPath, Collections.emptyMap());
        JSONArray data = response.getJSONArray("data");
        if (data == null) {
            return;
        }
        for (int i = 0; i < data.size(); i++) {
            JSONObject item = data.getJSONObject(i);
            if (item == null) {
                continue;
            }
            String title = cleanText(item.getStr("title"));
            if (StrUtil.isBlank(title)) {
                continue;
            }
            String digest = cleanText(item.getStr("digest"));
            String sourceName = StrUtil.blankToDefault(cleanText(item.getStr("source")), "网易新闻");
            String mergedText = title + " " + StrUtil.blankToDefault(digest, "");
            List<String> matchedKeywords = extractMatchedKeywords(mergedText, keywordWeights);
            if (!shouldKeepLocalizedCandidate(mergedText, matchedKeywords, profile)) {
                continue;
            }
            int localizedHeat = computeLocalizedHeat(mergedText, sourceName, 0, i, matchedKeywords, keywordWeights, profile);
            mergeHotItem(
                    deduped,
                    localizedItem(
                            title,
                            StrUtil.isNotBlank(digest) ? digest : sourceName + " 头条，已按城市关键词本地化排序",
                            StrUtil.blankToDefault(item.getStr("m_url"), item.getStr("pc_url")),
                            item.getStr("imgsrc"),
                            "网易新闻头条",
                            parseBoardTime(item.getStr("time")),
                            localizedHeat,
                            matchedKeywords,
                            true,
                            false
                    )
            );
        }
    }

    private void collectAlapiMorningBriefing(Map<String, CityHotNewsDTO> deduped, CityProfileDTO profile,
                                             Map<String, Integer> keywordWeights) {
        JSONObject response = postAlapi(alapiZaobaoPath, Collections.emptyMap());
        JSONObject data = response.getJSONObject("data");
        if (data == null) {
            return;
        }
        JSONArray news = data.getJSONArray("news");
        if (news == null) {
            return;
        }
        String image = data.getStr("image");
        LocalDateTime publishTime = parseBoardTime(data.getStr("date"));
        for (int i = 0; i < news.size(); i++) {
            String raw = cleanText(news.getStr(i));
            if (StrUtil.isBlank(raw)) {
                continue;
            }
            String title = raw;
            String summary = "每日早报要点，已按城市词命中和热度做本地化排序";
            int splitIndex = raw.indexOf('，');
            if (splitIndex > 0 && splitIndex < raw.length() - 1) {
                title = raw.substring(0, splitIndex);
                summary = raw.substring(splitIndex + 1);
            }
            List<String> matchedKeywords = extractMatchedKeywords(raw, keywordWeights);
            if (!shouldKeepLocalizedCandidate(raw, matchedKeywords, profile)) {
                continue;
            }
            int localizedHeat = computeLocalizedHeat(raw, "每日早报", 0, i, matchedKeywords, keywordWeights, profile);
            mergeHotItem(
                    deduped,
                    localizedItem(
                            title,
                            summary,
                            "",
                            image,
                            "每日早报",
                            publishTime,
                            localizedHeat,
                            matchedKeywords,
                            true,
                            false
                    )
            );
        }
    }

    private void collectAlapiTophub(Map<String, CityHotNewsDTO> deduped, CityProfileDTO profile,
                                    Map<String, Integer> keywordWeights, String siteId) {
        if (StrUtil.isBlank(siteId)) {
            return;
        }
        JSONObject response = postAlapi(alapiTophubPath, new LinkedHashMap<String, Object>() {{
            put("id", siteId);
            put("date", LocalDate.now().toString());
        }});
        JSONObject data = response.getJSONObject("data");
        if (data == null) {
            return;
        }
        JSONArray list = data.getJSONArray("list");
        if (list == null) {
            return;
        }
        String source = StrUtil.blankToDefault(data.getStr("name"), "ALAPI 热榜");
        LocalDateTime publishTime = parseBoardTime(data.getStr("last_update"), data.getStr("last_time"));
        for (int i = 0; i < list.size(); i++) {
            JSONObject item = list.getJSONObject(i);
            if (item == null) {
                continue;
            }
            String title = StrUtil.trim(item.getStr("title"));
            if (StrUtil.isBlank(title)) {
                continue;
            }
            String other = cleanText(item.getStr("other"));
            String mergedText = StrUtil.blankToDefault(title, "") + " " + StrUtil.blankToDefault(other, "");
            List<String> matchedKeywords = extractMatchedKeywords(mergedText, keywordWeights);
            if (!shouldKeepLocalizedCandidate(mergedText, matchedKeywords, profile)) {
                continue;
            }
            int rawHeat = parseInt(extractFirstNumber(other), 0);
            int localizedHeat = computeLocalizedHeat(mergedText, source, rawHeat, i, matchedKeywords, keywordWeights, profile);
            mergeHotItem(
                    deduped,
                    localizedItem(
                            title,
                            StrUtil.isNotBlank(other) ? source + " · " + shorten(other, 40) : source + " · 全国热榜本地化筛选",
                            item.getStr("link"),
                            item.getStr("image"),
                            source,
                            publishTime,
                            localizedHeat,
                            matchedKeywords,
                            true,
                            false
                    )
            );
        }
    }

    private JSONObject postAlapi(String path, Map<String, Object> body) {
        String normalizedPath = StrUtil.startWith(path, "/") ? path : "/" + path;
        String url = StrUtil.removeSuffix(StrUtil.blankToDefault(alapiBaseUrl, "https://v3.alapi.cn"), "/") + normalizedPath;
        String response = HttpRequest.post(url)
                .timeout(5000)
                .header("token", alapiToken)
                .header("Content-Type", "application/json;charset=UTF-8")
                .header("User-Agent", "HMDP-HotNewsBot/1.0 (+ALAPI)")
                .body(JSONUtil.toJsonStr(body == null ? Collections.emptyMap() : body))
                .execute()
                .body();
        JSONObject json = JSONUtil.parseObj(response);
        if (!json.getBool("success", false)) {
            throw new IllegalStateException("ALAPI request failed: " + json.getStr("message"));
        }
        return json;
    }

    private List<String> resolveAlapiTophubSiteIds() {
        JSONArray catalog = loadAlapiTophubSiteCatalog();
        List<String> resolved = new ArrayList<>();
        resolved.add(matchSiteId(catalog, "微博", "热搜榜"));
        resolved.add(matchSiteId(catalog, "微博", "要闻榜"));
        resolved.add(matchSiteId(catalog, "微博", "文娱榜"));
        resolved.add(matchSiteId(catalog, "百度", "热搜榜"));
        resolved.add(matchSiteId(catalog, "今日头条", "头条热榜"));
        resolved.add(matchSiteId(catalog, "抖音", "热点榜"));
        List<String> filtered = new ArrayList<>();
        for (String item : resolved) {
            if (StrUtil.isNotBlank(item) && !filtered.contains(item)) {
                filtered.add(item);
            }
        }
        if (!filtered.isEmpty()) {
            return filtered;
        }
        return new ArrayList<>(Arrays.asList(StrUtil.splitToArray(StrUtil.blankToDefault(alapiDefaultSiteIds, ""), ',')));
    }

    private JSONArray loadAlapiTophubSiteCatalog() {
        String cached = stringRedisTemplate.opsForValue().get(ALAPI_SITE_CACHE_KEY);
        if (StrUtil.isNotBlank(cached)) {
            return JSONUtil.parseArray(cached);
        }
        try {
            JSONObject response = postAlapi(alapiTophubSitePath, new LinkedHashMap<String, Object>() {{
                put("page", "1");
                put("page_size", "100");
            }});
            JSONArray data = response.getJSONArray("data");
            if (data != null) {
                stringRedisTemplate.opsForValue().set(ALAPI_SITE_CACHE_KEY, JSONUtil.toJsonStr(data), Math.max(1, alapiSiteCacheHours), TimeUnit.HOURS);
            }
            return data == null ? new JSONArray() : data;
        } catch (Exception ignored) {
            // The current token may not have "今日热榜" permission. We still keep Weibo-localized results available.
        }
        return new JSONArray();
    }

    private String matchSiteId(JSONArray catalog, String siteName, String categoryName) {
        if (catalog == null) {
            return "";
        }
        for (int i = 0; i < catalog.size(); i++) {
            JSONObject item = catalog.getJSONObject(i);
            if (item == null) {
                continue;
            }
            String site = StrUtil.blankToDefault(item.getStr("site"), "");
            String category = StrUtil.blankToDefault(item.getStr("category"), "");
            String title = StrUtil.blankToDefault(item.getStr("title"), "");
            if (site.contains(siteName) && (category.contains(categoryName) || title.contains(categoryName))) {
                return item.getStr("id");
            }
        }
        return "";
    }

    private LinkedHashMap<String, Integer> buildLegacyLocalKeywordWeights(CityProfileDTO profile) {
        LinkedHashMap<String, Integer> weights = new LinkedHashMap<>();
        String cityName = profile == null ? "本城" : profile.getCityName();
        addWeightedKeyword(weights, cityName, 34, false, cityName);
        addWeightedKeyword(weights, cityName + "热搜", 32, false, cityName);
        addWeightedKeyword(weights, cityName + "同城", 30, false, cityName);
        addWeightedKeyword(weights, cityName + "超话", 30, false, cityName);
        addWeightedKeyword(weights, cityName + "夜宵", 24, false, cityName);
        addWeightedKeyword(weights, cityName + "美食", 22, false, cityName);
        addWeightedKeyword(weights, cityName + "周末", 18, false, cityName);
        if (profile != null) {
            addWeightedKeyword(weights, profile.getProvince(), 12, false, cityName);
            addWeightedKeywords(weights, profile.getFeaturedDistricts(), 28, true, cityName);
            addWeightedKeywords(weights, profile.getHotSearches(), 24, false, cityName);
            addWeightedKeywords(weights, profile.getFeaturedRoutes(), 20, false, cityName);
            addWeightedKeywords(weights, profile.getDefaultScenes(), 16, true, cityName);
            addWeightedKeywords(weights, profile.getCultureTags(), 12, true, cityName);
            addWeightedKeywords(weights, profile.getSeasonalHooks(), 12, false, cityName);
        }
        return weights;
    }

    private LinkedHashMap<String, Integer> buildLocalKeywordWeights(CityProfileDTO profile) {
        LinkedHashMap<String, Integer> weights = new LinkedHashMap<>();
        String cityName = profile == null || StrUtil.isBlank(profile.getCityName()) ? "本城" : profile.getCityName();
        addWeightedKeyword(weights, cityName, 34, false, cityName);
        addWeightedKeyword(weights, cityName + "新闻", 30, false, cityName);
        addWeightedKeyword(weights, cityName + "热点", 28, false, cityName);
        addWeightedKeyword(weights, cityName + "同城", 24, false, cityName);
        if (profile != null) {
            addWeightedKeyword(weights, profile.getProvince(), 12, false, cityName);
            addWeightedKeywords(weights, profile.getFeaturedDistricts(), 28, true, cityName);
        }
        return weights;
    }

    private void addWeightedKeywords(Map<String, Integer> weights, List<String> keywords, int weight, boolean withCityPrefix, String cityName) {
        if (keywords == null) {
            return;
        }
        for (String keyword : keywords) {
            addWeightedKeyword(weights, keyword, weight, withCityPrefix, cityName);
        }
    }

    private void addWeightedKeyword(Map<String, Integer> weights, String keyword, int weight, boolean withCityPrefix, String cityName) {
        String normalized = normalizeKeyword(keyword);
        if (StrUtil.length(normalized) < 2) {
            return;
        }
        weights.putIfAbsent(normalized, weight);
        if (withCityPrefix && StrUtil.isNotBlank(cityName) && !normalized.contains(normalizeKeyword(cityName)) && normalized.length() <= 8) {
            weights.putIfAbsent(normalizeKeyword(cityName + normalized), weight + 4);
        }
    }

    private List<String> extractMatchedKeywords(String text, Map<String, Integer> keywordWeights) {
        String normalizedText = normalizeKeyword(text);
        if (StrUtil.isBlank(normalizedText) || keywordWeights == null || keywordWeights.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (String keyword : keywordWeights.keySet()) {
            if (normalizedText.contains(keyword)) {
                result.add(keyword);
            }
        }
        return result;
    }

    private int computeLocalizedHeat(String text, String source, int rawHeat, int rank,
                                     List<String> matchedKeywords, Map<String, Integer> keywordWeights,
                                     CityProfileDTO profile) {
        int score = normalizeSourceHeat(rawHeat, rank);
        String normalizedText = normalizeKeyword(text);
        String cityName = normalizeKeyword(profile == null ? "本城" : profile.getCityName());
        if (StrUtil.isNotBlank(cityName) && normalizedText.contains(cityName)) {
            score += 18;
        }
        int boost = 0;
        for (String keyword : matchedKeywords) {
            boost += Math.max(6, keywordWeights.getOrDefault(keyword, 10) / 2);
            if (boost >= 42) {
                break;
            }
        }
        score += boost;
        if (StrUtil.contains(source, "微博") && !matchedKeywords.isEmpty()) {
            score += 10;
        }
        if (matchedKeywords.isEmpty()) {
            score -= 18;
        }
        return Math.max(24, Math.min(100, score));
    }

    private int normalizeSourceHeat(int rawHeat, int rank) {
        if (rawHeat <= 0) {
            return Math.max(36, 92 - Math.min(rank, 12) * 4);
        }
        double value = Math.log10(rawHeat + 10D) * 9.0 + 24;
        return Math.max(40, Math.min(96, (int) Math.round(value)));
    }

    private void mergeHotItem(Map<String, CityHotNewsDTO> deduped, CityHotNewsDTO item) {
        if (item == null || StrUtil.isBlank(item.getTitle())) {
            return;
        }
        String key = normalizeTitleKey(item.getTitle());
        CityHotNewsDTO existing = deduped.get(key);
        if (existing == null || safeInt(item.getHeat()) > safeInt(existing.getHeat())) {
            deduped.put(key, item);
        }
    }

    private CityHotNewsDTO localizedItem(String title, String summary, String link, String image, String source,
                                         LocalDateTime publishTime, Integer heat, List<String> keywords,
                                         boolean realSource, boolean fallback) {
        CityHotNewsDTO dto = new CityHotNewsDTO();
        dto.setId(DigestUtil.md5Hex(StrUtil.blankToDefault(link, title)));
        dto.setTitle(title);
        dto.setSummary(summary);
        dto.setSource(source);
        dto.setSourceUrl(link);
        dto.setImage(image);
        dto.setPublishTime(publishTime == null ? LocalDateTime.now() : publishTime);
        dto.setHeat(heat);
        dto.setKeywords(keywords == null || keywords.isEmpty() ? Collections.emptyList() : keywords);
        dto.setMatchedKeywords(keywords == null || keywords.isEmpty() ? Collections.emptyList() : keywords);
        dto.setLocalizedScore(heat);
        dto.setDebugReason(buildDebugReason(source, keywords, heat));
        dto.setRealSource(realSource);
        dto.setFallback(fallback);
        return dto;
    }

    private List<CityHotNewsDTO> rankLocalizedNews(Map<String, CityHotNewsDTO> deduped, CityProfileDTO profile,
                                                   Map<String, Integer> keywordWeights) {
        List<CityHotNewsDTO> ranked = new ArrayList<>(deduped.values());
        ranked.sort((left, right) -> {
            int byHeat = Integer.compare(safeInt(right.getHeat()), safeInt(left.getHeat()));
            if (byHeat != 0) {
                return byHeat;
            }
            return safeTime(right.getPublishTime()).compareTo(safeTime(left.getPublishTime()));
        });
        List<CityHotNewsDTO> localFirst = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        String cityKeyword = normalizeKeyword(profile == null ? "" : profile.getCityName());
        for (CityHotNewsDTO item : ranked) {
            boolean hasMatchedKeywords = item.getMatchedKeywords() != null && !item.getMatchedKeywords().isEmpty();
            boolean containsCityName = StrUtil.isNotBlank(cityKeyword)
                    && normalizeKeyword(item.getTitle() + " " + StrUtil.blankToDefault(item.getSummary(), "")).contains(cityKeyword);
            if ((hasMatchedKeywords || containsCityName) && seen.add(normalizeTitleKey(item.getTitle()))) {
                localFirst.add(item);
            }
        }
        if (localFirst.size() < MIN_LOCAL_ITEMS) {
            for (CityHotNewsDTO item : ranked) {
                if (seen.add(normalizeTitleKey(item.getTitle()))) {
                    localFirst.add(item);
                }
                if (localFirst.size() >= MIN_LOCAL_ITEMS) {
                    break;
                }
            }
        }
        return localFirst;
    }

    private void hydrateHotNewsAssets(List<CityHotNewsDTO> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (CityHotNewsDTO item : items) {
            if (item == null || StrUtil.isBlank(item.getTitle())) {
                continue;
            }
            try {
                applyArticleAsset(item, resolveArticleAsset(item));
            } catch (Exception ignored) {
                // Asset hydration is best-effort. Keep the text news card even if preview resolution fails.
            }
        }
    }

    private ArticleAsset resolveArticleAsset(CityHotNewsDTO item) {
        String cacheSeed = StrUtil.blankToDefault(item.getSourceUrl(), item.getTitle());
        String cacheKey = ARTICLE_ASSET_PREFIX + DigestUtil.md5Hex(cacheSeed);
        ArticleAsset cached = readArticleAsset(cacheKey);
        if (cached != null && (StrUtil.isNotBlank(cached.directUrl) || StrUtil.isNotBlank(cached.imageUrl))) {
            return cached;
        }

        String originalUrl = StrUtil.blankToDefault(item.getSourceUrl(), "");
        String directUrl = resolveDirectArticleUrl(originalUrl);
        String imageUrl = normalizeAbsoluteUrl(directUrl, item.getImage());
        if (StrUtil.isBlank(imageUrl) && StrUtil.isNotBlank(directUrl)) {
            imageUrl = extractPreviewImageFromArticleUrl(directUrl);
        }

        ArticleAsset asset = new ArticleAsset(
                StrUtil.blankToDefault(directUrl, originalUrl),
                imageUrl
        );
        cacheArticleAsset(cacheKey, asset);
        return asset;
    }

    private void applyArticleAsset(CityHotNewsDTO item, ArticleAsset asset) {
        if (item == null || asset == null) {
            return;
        }
        if (StrUtil.isNotBlank(asset.directUrl)) {
            item.setSourceUrl(asset.directUrl);
        }
        if (StrUtil.isNotBlank(asset.imageUrl)) {
            item.setImage(asset.imageUrl);
        }
    }

    private ArticleAsset readArticleAsset(String cacheKey) {
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (StrUtil.isBlank(cached)) {
            return null;
        }
        JSONObject json = JSONUtil.parseObj(cached);
        return new ArticleAsset(
                json.getStr("directUrl"),
                json.getStr("imageUrl")
        );
    }

    private void cacheArticleAsset(String cacheKey, ArticleAsset asset) {
        if (asset == null) {
            return;
        }
        JSONObject json = new JSONObject();
        json.set("directUrl", asset.directUrl);
        json.set("imageUrl", asset.imageUrl);
        stringRedisTemplate.opsForValue().set(
                cacheKey,
                json.toString(),
                Math.max(1, assetCacheHours == null ? 12L : assetCacheHours),
                TimeUnit.HOURS
        );
    }

    private String resolveDirectArticleUrl(String url) {
        if (StrUtil.isBlank(url) || !isGoogleNewsWrapper(url)) {
            return url;
        }
        String html = HttpRequest.get(url)
                .timeout(5000)
                .setFollowRedirects(true)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Mobile Safari/537.36")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .execute()
                .body();
        String articleId = firstMatch(html, GOOGLE_NEWS_ID_PATTERN);
        String articleTs = firstMatch(html, GOOGLE_NEWS_TS_PATTERN);
        String articleSg = firstMatch(html, GOOGLE_NEWS_SG_PATTERN);
        if (StrUtil.hasBlank(articleId, articleTs, articleSg)) {
            return url;
        }

        String rpcPayload = String.format(Locale.ROOT,
                "[[[\"Fbv4je\",\"[\\\"garturlreq\\\",[[\\\"en-US\\\",\\\"US\\\",[\\\"FINANCE_TOP_INDICES\\\",\\\"GENESIS_PUBLISHER_SECTION\\\",\\\"WEB_TEST_1_0_0\\\"],null,null,1,1,\\\"US:en\\\",null,null,null,null,null,null,null,false,5],\\\"en-US\\\",\\\"US\\\",true,[3,5,9,19],1,true,\\\"903280278\\\",null,null,null,false],\\\"%s\\\",%s,\\\"%s\\\"]\",null,\"generic\"]]]",
                articleId,
                articleTs,
                articleSg
        );
        String response = HttpRequest.post(GOOGLE_BATCH_EXECUTE_URL)
                .timeout(5000)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Mobile Safari/537.36")
                .header("Referer", "https://news.google.com/")
                .form("f.req", rpcPayload)
                .execute()
                .body();
        String resolvedUrl = decodeGoogleBatchUrl(response);
        return StrUtil.isNotBlank(resolvedUrl) ? resolvedUrl : url;
    }

    private String decodeGoogleBatchUrl(String response) {
        String raw = firstMatch(response, GOOGLE_NEWS_RESOLVED_URL_PATTERN);
        if (StrUtil.isBlank(raw)) {
            return "";
        }
        return raw
                .replace("\\u003d", "=")
                .replace("\\u0026", "&")
                .replace("\\u002F", "/")
                .replace("\\/", "/")
                .replace("\\\\", "\\")
                .trim();
    }

    private boolean isGoogleNewsWrapper(String url) {
        if (StrUtil.isBlank(url)) {
            return false;
        }
        try {
            URI uri = URI.create(url);
            return "news.google.com".equalsIgnoreCase(uri.getHost())
                    && StrUtil.contains(uri.getPath(), "/rss/articles/");
        } catch (Exception ignored) {
            return false;
        }
    }

    private String extractPreviewImageFromArticleUrl(String articleUrl) {
        if (StrUtil.isBlank(articleUrl)) {
            return "";
        }
        String html = HttpRequest.get(articleUrl)
                .timeout(5000)
                .setFollowRedirects(true)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Mobile Safari/537.36")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .execute()
                .body();
        return extractPreviewImageFromHtml(html, articleUrl);
    }

    private String extractPreviewImageFromHtml(String html, String articleUrl) {
        if (StrUtil.isBlank(html)) {
            return "";
        }
        String imageUrl = extractMetaImage(html, articleUrl, "og:image");
        if (StrUtil.isBlank(imageUrl)) {
            imageUrl = extractMetaImage(html, articleUrl, "twitter:image");
        }
        if (StrUtil.isBlank(imageUrl)) {
            imageUrl = extractMetaImage(html, articleUrl, "og:image:url");
        }
        if (StrUtil.isBlank(imageUrl)) {
            imageUrl = extractLinkImage(html, articleUrl);
        }
        if (StrUtil.isBlank(imageUrl)) {
            imageUrl = extractBodyImage(html, articleUrl);
        }
        return imageUrl;
    }

    private String extractMetaImage(String html, String articleUrl, String expectedName) {
        Matcher matcher = META_TAG_PATTERN.matcher(html);
        while (matcher.find()) {
            Map<String, String> attrs = parseTagAttributes(matcher.group());
            String marker = StrUtil.blankToDefault(attrs.get("property"), attrs.get("name"));
            if (StrUtil.isBlank(marker)) {
                marker = attrs.get("itemprop");
            }
            if (expectedName.equalsIgnoreCase(StrUtil.blankToDefault(marker, ""))) {
                String candidate = normalizeAbsoluteUrl(articleUrl, attrs.get("content"));
                if (isUsablePreviewImage(candidate)) {
                    return candidate;
                }
            }
        }
        return "";
    }

    private String extractLinkImage(String html, String articleUrl) {
        Matcher matcher = LINK_TAG_PATTERN.matcher(html);
        while (matcher.find()) {
            Map<String, String> attrs = parseTagAttributes(matcher.group());
            if ("image_src".equalsIgnoreCase(StrUtil.blankToDefault(attrs.get("rel"), ""))) {
                String candidate = normalizeAbsoluteUrl(articleUrl, attrs.get("href"));
                if (isUsablePreviewImage(candidate)) {
                    return candidate;
                }
            }
        }
        return "";
    }

    private String extractBodyImage(String html, String articleUrl) {
        Matcher matcher = IMAGE_PATTERN.matcher(StrUtil.blankToDefault(html, ""));
        while (matcher.find()) {
            String candidate = normalizeAbsoluteUrl(articleUrl, matcher.group(1));
            if (isUsablePreviewImage(candidate)) {
                return candidate;
            }
        }
        return "";
    }

    private Map<String, String> parseTagAttributes(String tag) {
        Map<String, String> attributes = new LinkedHashMap<>();
        Matcher matcher = ATTRIBUTE_PATTERN.matcher(StrUtil.blankToDefault(tag, ""));
        while (matcher.find()) {
            attributes.put(
                    matcher.group(1).toLowerCase(Locale.ROOT),
                    HtmlUtil.unescape(StrUtil.blankToDefault(matcher.group(3), "").trim())
            );
        }
        return attributes;
    }

    private String normalizeAbsoluteUrl(String baseUrl, String candidate) {
        String value = HtmlUtil.unescape(StrUtil.blankToDefault(candidate, "").trim());
        if (StrUtil.isBlank(value)) {
            return "";
        }
        if (value.startsWith("//")) {
            return "https:" + value;
        }
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value;
        }
        if (value.startsWith("data:") || value.startsWith("javascript:")) {
            return "";
        }
        if (StrUtil.isBlank(baseUrl)) {
            return "";
        }
        try {
            return URI.create(baseUrl).resolve(value).toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private boolean isUsablePreviewImage(String imageUrl) {
        if (StrUtil.isBlank(imageUrl) || imageUrl.contains("${")) {
            return false;
        }
        String lower = imageUrl.toLowerCase(Locale.ROOT);
        if (!(lower.startsWith("http://") || lower.startsWith("https://"))) {
            return false;
        }
        List<String> blockedTokens = Arrays.asList(
                "logo",
                "icon",
                "favicon",
                "avatar",
                "placeholder",
                "default",
                "qrcode",
                "/qr-",
                "/qr_",
                "/qr/",
                "fd-img",
                "copyinfo",
                "media_flow_tag",
                "home.png",
                "zan1",
                "zan2",
                "ask.png",
                "top.png"
        );
        for (String token : blockedTokens) {
            if (lower.contains(token)) {
                return false;
            }
        }
        return true;
    }

    private String firstMatch(String value, Pattern pattern) {
        if (StrUtil.isBlank(value) || pattern == null) {
            return "";
        }
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? StrUtil.blankToDefault(matcher.group(1), "") : "";
    }

    private String buildDebugReason(String source, List<String> matchedKeywords, Integer heat) {
        List<String> safeKeywords = matchedKeywords == null ? Collections.emptyList() : matchedKeywords;
        if (safeKeywords.isEmpty()) {
            return source + "：未命中强本地词，保留基础热度 " + safeInt(heat);
        }
        return source + "：命中本地词 " + String.join("、", safeKeywords) + "，本地化得分 " + safeInt(heat);
    }

    private void cachePayload(String cacheKey, String staleKey, Map<String, Object> payload) {
        String json = JSONUtil.toJsonStr(payload);
        stringRedisTemplate.opsForValue().set(cacheKey, json, Math.max(5, ttlMinutes), TimeUnit.MINUTES);
        stringRedisTemplate.opsForValue().set(staleKey, json, Math.max(1, staleHours), TimeUnit.HOURS);
    }

    private List<CityHotNewsDTO> crawlLegacyPublicRss(CityProfileDTO profile, Integer current) throws Exception {
        String cityName = profile == null || StrUtil.isBlank(profile.getCityName()) ? "本地" : profile.getCityName();
        List<String> queries = new ArrayList<>();
        queries.add(cityName + " 热点");
        queries.add(cityName + " 今日 新闻");
        queries.add(cityName + " 本地 生活");

        int start = Math.max(0, ((current == null ? 1 : current) - 1) * 8);
        int limit = Math.max(8, Math.min(maxItems == null ? 20 : maxItems, 30));
        Map<String, CityHotNewsDTO> deduped = new LinkedHashMap<>();
        for (String query : queries) {
            if (deduped.size() >= limit) {
                break;
            }
            String url = googleNewsRssUrl
                    + "?q=" + URLEncoder.encode(query, "UTF-8")
                    + "&format=rss&cc=cn&mkt=zh-CN";
            if (!isAllowedSourceUrl(url)) {
                continue;
            }
            String body = HttpRequest.get(url)
                    .timeout(5000)
                    .header("User-Agent", "HMDP-HotNewsBot/1.0 (+public RSS; low frequency)")
                    .execute()
                    .body();
            for (CityHotNewsDTO item : parseRss(body, cityName, query)) {
                if (deduped.size() >= limit + start) {
                    break;
                }
                deduped.putIfAbsent(normalizeTitleKey(item.getTitle()), item);
            }
        }
        List<CityHotNewsDTO> all = new ArrayList<>(deduped.values());
        if (start >= all.size()) {
            return all;
        }
        return all.subList(start, Math.min(all.size(), start + limit));
    }

    private List<CityHotNewsDTO> crawlPublicRss(CityProfileDTO profile, Integer current) throws Exception {
        LinkedHashMap<String, Integer> keywordWeights = buildLocalKeywordWeights(profile);
        int start = Math.max(0, ((current == null ? 1 : current) - 1) * 8);
        int limit = Math.max(8, Math.min(maxItems == null ? 20 : maxItems, 30));
        Map<String, CityHotNewsDTO> deduped = new LinkedHashMap<>();
        collectCityPublicNews(deduped, profile, keywordWeights);
        List<CityHotNewsDTO> all = rankLocalizedNews(deduped, profile, keywordWeights);
        if (start >= all.size()) {
            return all;
        }
        return all.subList(start, Math.min(all.size(), start + limit));
    }

    private boolean isAllowedSourceUrl(String url) {
        if (StrUtil.isBlank(url)) {
            return false;
        }
        try {
            URI uri = URI.create(url);
            String host = StrUtil.blankToDefault(uri.getHost(), "").toLowerCase(Locale.ROOT);
            if (StrUtil.isBlank(host)) {
                return false;
            }
            return Arrays.stream(StrUtil.splitToArray(StrUtil.blankToDefault(allowedHosts, ""), ','))
                    .map(item -> StrUtil.trim(item).toLowerCase(Locale.ROOT))
                    .filter(StrUtil::isNotBlank)
                    .anyMatch(allowed -> host.equals(allowed) || host.endsWith("." + allowed));
        } catch (Exception ignored) {
            return false;
        }
    }

    private List<CityHotNewsDTO> parseRss(String xml, String cityName, String query) throws Exception {
        List<CityHotNewsDTO> list = new ArrayList<>();
        if (StrUtil.isBlank(xml)) {
            return list;
        }
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (Exception ignored) {
        }
        Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        NodeList items = document.getElementsByTagName("item");
        for (int i = 0; i < items.getLength(); i++) {
            Node node = items.item(i);
            if (!(node instanceof Element)) {
                continue;
            }
            Element item = (Element) node;
            String title = cleanText(childText(item, "title"));
            String link = childText(item, "link");
            if (StrUtil.isBlank(title) || StrUtil.isBlank(link)) {
                continue;
            }
            String description = childText(item, "description");
            CityHotNewsDTO dto = new CityHotNewsDTO();
            dto.setId(DigestUtil.md5Hex(link));
            dto.setTitle(title);
            dto.setSummary(shorten(cleanText(description), 76));
            dto.setSource(defaultIfBlank(childText(item, "source"), "Bing News"));
            dto.setSourceUrl(link);
            dto.setImage(extractImage(description));
            dto.setPublishTime(parsePublishTime(childText(item, "pubDate")));
            dto.setHeat(buildHeat(title, cityName, i));
            dto.setKeywords(buildKeywords(cityName, query, title));
            dto.setMatchedKeywords(extractMatchedKeywords(title + " " + query, buildLocalKeywordWeights(simpleCityProfile(cityName))));
            dto.setLocalizedScore(dto.getHeat());
            dto.setDebugReason("RSS 补充源：命中城市词后提升排序，适合作为 ALAPI 不足时的二级兜底");
            dto.setRealSource(true);
            dto.setFallback(false);
            list.add(dto);
        }
        return list;
    }

    private List<CityHotNewsDTO> parseLegacyLocalizedRss(String xml, CityProfileDTO profile, String query,
                                                   Map<String, Integer> keywordWeights) throws Exception {
        List<CityHotNewsDTO> list = new ArrayList<>();
        if (StrUtil.isBlank(xml)) {
            return list;
        }
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (Exception ignored) {
        }
        Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        NodeList items = document.getElementsByTagName("item");
        String cityName = profile == null || StrUtil.isBlank(profile.getCityName()) ? "本地" : profile.getCityName();
        for (int i = 0; i < items.getLength(); i++) {
            Node node = items.item(i);
            if (!(node instanceof Element)) {
                continue;
            }
            Element item = (Element) node;
            String title = cleanText(childText(item, "title"));
            String link = childText(item, "link");
            if (StrUtil.isBlank(title) || StrUtil.isBlank(link)) {
                continue;
            }
            String description = childText(item, "description");
            String mergedText = title + " " + cleanText(description) + " " + query;
            List<String> matchedKeywords = extractMatchedKeywords(mergedText, keywordWeights);
            int localizedHeat = Math.min(100, computeLocalizedHeat(
                    mergedText,
                    "城市新闻搜索",
                    0,
                    i,
                    matchedKeywords,
                    keywordWeights,
                    profile
            ) + 10);
            CityHotNewsDTO dto = new CityHotNewsDTO();
            dto.setId(DigestUtil.md5Hex(link));
            dto.setTitle(title);
            dto.setSummary(shorten(cleanText(description), 76));
            dto.setSource(defaultIfBlank(childText(item, "source"), "城市新闻搜索"));
            dto.setSourceUrl(link);
            dto.setImage(extractImage(description));
            dto.setPublishTime(parsePublishTime(childText(item, "pubDate")));
            dto.setHeat(localizedHeat);
            dto.setKeywords(buildKeywords(cityName, query, title));
            dto.setMatchedKeywords(matchedKeywords);
            dto.setLocalizedScore(localizedHeat);
            dto.setDebugReason("城市新闻搜索：按当前定位城市“" + cityName + "”自动搜索，命中 "
                    + (matchedKeywords.isEmpty() ? "城市主题词" : String.join("、", matchedKeywords)));
            dto.setRealSource(true);
            dto.setFallback(false);
            list.add(dto);
        }
        return list;
    }

    private List<CityHotNewsDTO> parseLocalizedRss(String xml, CityProfileDTO profile, String query,
                                                   Map<String, Integer> keywordWeights) throws Exception {
        List<CityHotNewsDTO> list = new ArrayList<>();
        if (StrUtil.isBlank(xml)) {
            return list;
        }
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (Exception ignored) {
        }
        Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        NodeList items = document.getElementsByTagName("item");
        String cityName = profile == null || StrUtil.isBlank(profile.getCityName()) ? "本地" : profile.getCityName();
        for (int i = 0; i < items.getLength(); i++) {
            Node node = items.item(i);
            if (!(node instanceof Element)) {
                continue;
            }
            Element item = (Element) node;
            String title = cleanText(childText(item, "title"));
            String link = childText(item, "link");
            if (StrUtil.isBlank(title) || StrUtil.isBlank(link)) {
                continue;
            }
            String description = childText(item, "description");
            String sourceName = defaultIfBlank(childText(item, "source"), "城市新闻搜索");
            String contentText = title + " " + cleanText(description) + " " + sourceName;
            List<String> matchedKeywords = extractMatchedKeywords(contentText, keywordWeights);
            if (!shouldKeepLocalizedCandidate(contentText, matchedKeywords, profile)) {
                continue;
            }
            LocalDateTime publishTime = parsePublishTime(childText(item, "pubDate"));
            if (!isRecentHotNews(publishTime)) {
                continue;
            }
            int localizedHeat = Math.min(100, computeLocalizedHeat(
                    contentText,
                    "城市新闻搜索",
                    0,
                    i,
                    matchedKeywords,
                    keywordWeights,
                    profile
            ) + 16);
            CityHotNewsDTO dto = new CityHotNewsDTO();
            dto.setId(DigestUtil.md5Hex(link));
            dto.setTitle(title);
            dto.setSummary(shorten(cleanText(description), 76));
            dto.setSource(sourceName);
            dto.setSourceUrl(link);
            dto.setImage(extractImage(description));
            dto.setPublishTime(publishTime);
            dto.setHeat(localizedHeat);
            dto.setKeywords(buildKeywords(cityName, query, title));
            dto.setMatchedKeywords(matchedKeywords);
            dto.setLocalizedScore(localizedHeat);
            dto.setDebugReason("城市新闻搜索：按当前定位城市“" + cityName + "”自动搜索，命中 "
                    + (matchedKeywords.isEmpty() ? "城市主题词" : String.join("、", matchedKeywords)));
            dto.setRealSource(true);
            dto.setFallback(false);
            list.add(dto);
        }
        return list;
    }

    private String buildGoogleNewsSearchUrl(String query) throws Exception {
        return StrUtil.removeSuffix(StrUtil.blankToDefault(googleNewsRssUrl, "https://news.google.com/rss/search"), "/")
                + "?q=" + URLEncoder.encode(query, "UTF-8")
                + "&hl=zh-CN&gl=CN&ceid=CN:zh-Hans";
    }

    private boolean shouldKeepLocalizedCandidate(String text, List<String> matchedKeywords, CityProfileDTO profile) {
        if (matchedKeywords != null && !matchedKeywords.isEmpty()) {
            return true;
        }
        return hasLocationSignal(text, profile);
    }

    private boolean hasLocationSignal(String text, CityProfileDTO profile) {
        String normalizedText = normalizeKeyword(text);
        if (StrUtil.isBlank(normalizedText)) {
            return false;
        }
        if (profile != null) {
            if (StrUtil.isNotBlank(profile.getCityName()) && normalizedText.contains(normalizeKeyword(profile.getCityName()))) {
                return true;
            }
            if (StrUtil.isNotBlank(profile.getProvince()) && normalizedText.contains(normalizeKeyword(profile.getProvince()))) {
                return true;
            }
            if (profile.getFeaturedDistricts() != null) {
                for (String district : profile.getFeaturedDistricts()) {
                    if (StrUtil.isNotBlank(district) && normalizedText.contains(normalizeKeyword(district))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isRecentHotNews(LocalDateTime publishTime) {
        int days = maxAgeDays == null ? 14 : Math.max(1, maxAgeDays);
        return publishTime != null && !publishTime.isBefore(LocalDateTime.now().minusDays(days));
    }

    private List<CityHotNewsDTO> buildLocalFallback(CityProfileDTO profile) {
        String cityCode = profile == null ? null : profile.getCityCode();
        String cityName = profile == null || StrUtil.isBlank(profile.getCityName()) ? "本城" : profile.getCityName();
        List<CityHotNewsDTO> list = new ArrayList<>();

        List<String> hotSearches = profile == null || profile.getHotSearches() == null
                ? Arrays.asList(cityName + "美食", cityName + "夜宵", cityName + "周末去哪")
                : profile.getHotSearches();
        int index = 0;
        for (String keyword : hotSearches) {
            list.add(fallbackItem(cityName + "正在热搜：" + keyword, "站内用户正在搜索和收藏相关店铺。", keyword, index++));
        }

        LambdaQueryWrapper<Shop> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(cityCode)) {
            wrapper.eq(Shop::getCityCode, cityCode);
        } else {
            wrapper.like(Shop::getCity, cityName);
        }
        wrapper.orderByDesc(Shop::getSold).last("limit 8");
        List<Shop> shops = shopMapper.selectList(wrapper);
        for (Shop shop : shops) {
            String title = (StrUtil.blankToDefault(shop.getArea(), cityName)) + "人气店上升：" + shop.getName();
            String summary = "评分 " + (shop.getScore() == null ? "暂无" : String.format(Locale.CHINA, "%.1f", shop.getScore() / 10.0))
                    + "，人均 " + (shop.getAvgPrice() == null ? "待补充" : ("¥" + shop.getAvgPrice())) + "。";
            CityHotNewsDTO item = fallbackItem(title, summary, shop.getName(), index++);
            item.setImage(firstImage(shop.getImages()));
            list.add(item);
        }
        return list;
    }

    private CityHotNewsDTO fallbackItem(String title, String summary, String keyword, int index) {
        CityHotNewsDTO dto = new CityHotNewsDTO();
        dto.setId(DigestUtil.md5Hex(title + index));
        dto.setTitle(title);
        dto.setSummary(summary);
        dto.setSource("站内热榜");
        dto.setSourceUrl("");
        dto.setPublishTime(LocalDateTime.now());
        dto.setHeat(Math.max(35, 96 - index * 7));
        dto.setKeywords(Arrays.asList(keyword));
        dto.setMatchedKeywords(Arrays.asList(normalizeKeyword(keyword)));
        dto.setLocalizedScore(dto.getHeat());
        dto.setDebugReason("站内热榜兜底：来自城市热搜词或高热店铺补位");
        dto.setRealSource(false);
        dto.setFallback(true);
        return dto;
    }

    private Map<String, Object> buildPayload(CityProfileDTO profile, List<CityHotNewsDTO> list, boolean realSource,
                                             boolean fallback, String source, String fallbackReason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> keywordWeights = buildLocalKeywordWeights(profile);
        int matchedLocalCount = 0;
        int realSourceCount = 0;
        int fallbackCount = 0;
        Map<String, Integer> sourceBreakdown = new LinkedHashMap<>();
        if (list != null) {
            for (CityHotNewsDTO item : list) {
                if (item == null) {
                    continue;
                }
                if (item.getMatchedKeywords() != null && !item.getMatchedKeywords().isEmpty()) {
                    matchedLocalCount++;
                }
                if (Boolean.TRUE.equals(item.getRealSource())) {
                    realSourceCount++;
                }
                if (Boolean.TRUE.equals(item.getFallback())) {
                    fallbackCount++;
                }
                String sourceName = StrUtil.blankToDefault(item.getSource(), "未知来源");
                sourceBreakdown.put(sourceName, sourceBreakdown.getOrDefault(sourceName, 0) + 1);
            }
        }
        payload.put("cityCode", profile == null ? "" : profile.getCityCode());
        payload.put("cityName", profile == null ? "本城" : profile.getCityName());
        payload.put("searchCity", profile == null ? "本城" : profile.getCityName());
        payload.put("searchMode", "location-first");
        payload.put("realSource", realSource);
        payload.put("fallback", fallback);
        payload.put("source", source);
        payload.put("fallbackReason", fallbackReason);
        payload.put("keywordPool", new ArrayList<>(keywordWeights.keySet()));
        payload.put("matchedLocalCount", matchedLocalCount);
        payload.put("realSourceCount", realSourceCount);
        payload.put("fallbackCount", fallbackCount);
        payload.put("sourceBreakdown", sourceBreakdown);
        payload.put("generatedAt", LocalDateTime.now());
        payload.put("list", list);
        payload.put("total", list == null ? 0 : list.size());
        return payload;
    }

    private String childText(Element item, String tagName) {
        NodeList list = item.getElementsByTagName(tagName);
        if (list == null || list.getLength() == 0 || list.item(0) == null) {
            return "";
        }
        return list.item(0).getTextContent();
    }

    private String cleanText(String value) {
        String normalized = HtmlUtil.unescape(HtmlUtil.cleanHtmlTag(StrUtil.blankToDefault(value, "")));
        normalized = normalized
                .replace('\u00A0', ' ')
                .replace("&nbsp;", " ")
                .replace("&ensp;", " ")
                .replace("&emsp;", " ")
                .replace("&amp;", "&");
        return normalized.replaceAll("\\s+", " ").trim();
    }

    private String shorten(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 1) + "…";
    }

    private String extractImage(String description) {
        if (StrUtil.isBlank(description)) {
            return "";
        }
        Matcher matcher = IMAGE_PATTERN.matcher(description);
        return matcher.find() ? matcher.group(1) : "";
    }

    private LocalDateTime parsePublishTime(String value) {
        if (StrUtil.isBlank(value)) {
            return LocalDateTime.now();
        }
        try {
            return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .withZoneSameInstant(ZoneId.systemDefault())
                    .toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            return LocalDateTime.ofInstant(new Date().toInstant(), ZoneId.systemDefault());
        }
    }

    private LocalDateTime parseBoardTime(String... candidates) {
        if (candidates == null) {
            return LocalDateTime.now();
        }
        for (String value : candidates) {
            if (StrUtil.isBlank(value)) {
                continue;
            }
            try {
                return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } catch (DateTimeParseException ignored) {
            }
            try {
                return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            } catch (DateTimeParseException ignored) {
            }
        }
        return LocalDateTime.now();
    }

    private Integer buildHeat(String title, String cityName, int index) {
        int base = 96 - Math.min(index, 10) * 5;
        if (title != null && title.contains(cityName)) {
            base += 4;
        }
        return Math.max(42, Math.min(100, base));
    }

    private List<String> buildKeywords(String cityName, String query, String title) {
        Set<String> keywords = new LinkedHashSet<>();
        keywords.add(cityName);
        if (StrUtil.isNotBlank(query)) {
            for (String token : query.split("\\s+")) {
                if (StrUtil.isBlank(token) || token.startsWith("when:")) {
                    continue;
                }
                keywords.add(token);
            }
        }
        if (StrUtil.isNotBlank(title)) {
            for (String token : Arrays.asList("美食", "演出", "旅游", "消费", "天气", "交通", "夜市", "商场", "活动")) {
                if (title.contains(token)) {
                    keywords.add(token);
                }
            }
        }
        return new ArrayList<>(keywords);
    }

    private String normalizeTitleKey(String title) {
        return StrUtil.blankToDefault(title, "").replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private String defaultIfBlank(String value, String fallback) {
        return StrUtil.isBlank(value) ? fallback : value;
    }

    private String firstImage(String images) {
        if (StrUtil.isBlank(images)) {
            return "";
        }
        return images.split(",")[0];
    }

    private CityProfileDTO simpleCityProfile(String cityName) {
        CityProfileDTO profile = new CityProfileDTO();
        profile.setCityName(cityName);
        return profile;
    }

    private String normalizeKeyword(String keyword) {
        return StrUtil.blankToDefault(keyword, "")
                .replace("市", "")
                .replace("省", "")
                .replace("·", "")
                .replace("-", "")
                .replace("_", "")
                .replaceAll("\\s+", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private Integer safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private LocalDateTime safeTime(LocalDateTime value) {
        return value == null ? LocalDateTime.MIN : value;
    }

    private int parseInt(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            String text = String.valueOf(value).replaceAll("[^0-9]", "");
            if (StrUtil.isBlank(text)) {
                return fallback;
            }
            return Integer.parseInt(text);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String extractFirstNumber(String value) {
        if (StrUtil.isBlank(value)) {
            return "";
        }
        Matcher matcher = Pattern.compile("(\\d[\\d,]*)").matcher(value);
        return matcher.find() ? matcher.group(1).replace(",", "") : "";
    }

    private static class ArticleAsset {
        private final String directUrl;
        private final String imageUrl;

        private ArticleAsset(String directUrl, String imageUrl) {
            this.directUrl = directUrl;
            this.imageUrl = imageUrl;
        }
    }
}
