package com.hmdp.service;

import com.hmdp.dto.DeepSeekSearchDTO;
import com.hmdp.dto.Result;

/**
 * DeepSeek智能搜索服务
 */
public interface IDeepSeekSearchService {

    /**
     * 智能搜索
     *
     * @param searchDTO 搜索参数
     * @return {@link Result}
     */
    Result search(DeepSeekSearchDTO searchDTO);

    /**
     * 获取搜索建议（自动补全）
     *
     * @param prefix 前缀
     * @return {@link Result}
     */
    Result getSuggestions(String prefix);

    /**
     * 获取热门搜索
     *
     * @return {@link Result}
     */
    Result getHotSearch();

    /**
     * 获取个性化推荐
     *
     * @param current 当前页
     * @return {@link Result}
     */
    Result getRecommendations(Integer current);

    /**
     * 记录搜索历史
     *
     * @param keyword 关键词
     * @return {@link Result}
     */
    Result recordSearchHistory(String keyword);

    /**
     * 获取搜索历史
     *
     * @return {@link Result}
     */
    Result getSearchHistory();

    /**
     * 清空搜索历史
     *
     * @return {@link Result}
     */
    Result clearSearchHistory();
}
