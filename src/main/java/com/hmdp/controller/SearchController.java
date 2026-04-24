package com.hmdp.controller;

import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.DeepSeekSearchDTO;
import com.hmdp.dto.Result;
import com.hmdp.service.IDeepSeekSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Map;

/**
 * 兼容旧版搜索入口，内部统一委托给智能搜索服务
 */
@Slf4j
@RestController
@RequestMapping("/shop-search")
public class SearchController {

    @Resource
    private IDeepSeekSearchService deepSeekSearchService;

    /**
     * 综合搜索（兼容旧入口）
     */
    @PostMapping("")
    public Result search(@RequestBody Map<String, Object> request) {
        DeepSeekSearchDTO dto = new DeepSeekSearchDTO();
        dto.setKeyword(stringValue(request.get("keyword")));
        dto.setType(StrUtil.blankToDefault(stringValue(request.get("type")), "all"));
        dto.setSortBy(StrUtil.blankToDefault(stringValue(request.get("sortBy")), "relevance"));
        dto.setCurrent(intValue(request.get("current"), 1));
        dto.setLongitude(doubleValue(request.get("longitude")));
        dto.setLatitude(doubleValue(request.get("latitude")));
        dto.setTypeId(longValue(request.get("typeId")));
        dto.setMinPrice(longValue(request.get("minPrice")));
        dto.setMaxPrice(longValue(request.get("maxPrice")));
        dto.setCityCode(stringValue(request.get("cityCode")));

        log.info("Legacy /shop-search request delegated to /search service - keyword: {}, type: {}, sortBy: {}, current: {}",
                dto.getKeyword(), dto.getType(), dto.getSortBy(), dto.getCurrent());
        return deepSeekSearchService.search(dto);
    }

    /**
     * 获取热门搜索
     */
    @GetMapping("/hot")
    public Result getHotSearches() {
        return deepSeekSearchService.getHotSearch();
    }

    /**
     * 获取搜索历史
     */
    @GetMapping("/history")
    public Result getSearchHistory() {
        return deepSeekSearchService.getSearchHistory();
    }

    /**
     * 记录搜索历史
     */
    @PostMapping("/history")
    public Result recordSearchHistory(@RequestParam("keyword") String keyword) {
        return deepSeekSearchService.recordSearchHistory(keyword);
    }

    /**
     * 清空搜索历史
     */
    @DeleteMapping("/history")
    public Result clearSearchHistory() {
        return deepSeekSearchService.clearSearchHistory();
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        return String.valueOf(value).trim();
    }

    private Integer intValue(Object value, Integer defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        String text = String.valueOf(value).trim();
        if (StrUtil.isBlank(text)) {
            return defaultValue;
        }
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        String text = String.valueOf(value).trim();
        if (StrUtil.isBlank(text)) {
            return null;
        }
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double doubleValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        String text = String.valueOf(value).trim();
        if (StrUtil.isBlank(text)) {
            return null;
        }
        try {
            return Double.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
