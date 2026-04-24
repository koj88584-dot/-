package com.hmdp.controller;

import com.hmdp.dto.DeepSeekSearchDTO;
import com.hmdp.dto.Result;
import com.hmdp.service.IDeepSeekSearchService;
import com.hmdp.utils.LimitType;
import com.hmdp.utils.RateLimiter;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * DeepSeek智能搜索控制器
 */
@RestController
@RequestMapping("/search")
public class DeepSeekSearchController {

    @Resource
    private IDeepSeekSearchService deepSeekSearchService;

    /**
     * 智能搜索
     */
    @PostMapping
    @RateLimiter(key = "search", time = 60, count = 30, limitType = LimitType.IP, message = "搜索请求过于频繁，请稍后再试")
    public Result search(@RequestBody DeepSeekSearchDTO searchDTO) {
        return deepSeekSearchService.search(searchDTO);
    }

    /**
     * 简单搜索（GET方式）
     */
    @GetMapping
    @RateLimiter(key = "search", time = 60, count = 30, limitType = LimitType.IP, message = "搜索请求过于频繁，请稍后再试")
    public Result simpleSearch(@RequestParam("keyword") String keyword,
                               @RequestParam(value = "type", defaultValue = "all") String type,
                               @RequestParam(value = "sortBy", defaultValue = "relevance") String sortBy,
                               @RequestParam(value = "current", defaultValue = "1") Integer current,
                               @RequestParam(value = "cityCode", required = false) String cityCode) {
        DeepSeekSearchDTO dto = new DeepSeekSearchDTO();
        dto.setKeyword(keyword);
        dto.setType(type);
        dto.setSortBy(sortBy);
        dto.setCurrent(current);
        dto.setCityCode(cityCode);
        return deepSeekSearchService.search(dto);
    }

    /**
     * 获取搜索建议（自动补全）
     */
    @GetMapping("/suggestions")
    public Result getSuggestions(@RequestParam("prefix") String prefix) {
        return deepSeekSearchService.getSuggestions(prefix);
    }

    /**
     * 获取热门搜索
     */
    @GetMapping("/hot")
    public Result getHotSearch() {
        return deepSeekSearchService.getHotSearch();
    }

    /**
     * 获取个性化推荐
     */
    @GetMapping("/recommend")
    @RateLimiter(key = "recommend", time = 60, count = 20, limitType = LimitType.USER, message = "请求过于频繁，请稍后再试")
    public Result getRecommendations(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return deepSeekSearchService.getRecommendations(current);
    }

    /**
     * 记录搜索历史
     */
    @PostMapping("/history")
    public Result recordSearchHistory(@RequestParam("keyword") String keyword) {
        return deepSeekSearchService.recordSearchHistory(keyword);
    }

    /**
     * 获取搜索历史
     */
    @GetMapping("/history")
    public Result getSearchHistory() {
        return deepSeekSearchService.getSearchHistory();
    }

    /**
     * 清空搜索历史
     */
    @DeleteMapping("/history")
    public Result clearSearchHistory() {
        return deepSeekSearchService.clearSearchHistory();
    }
}
