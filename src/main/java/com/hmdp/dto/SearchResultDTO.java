package com.hmdp.dto;

import lombok.Data;
import java.util.List;

/**
 * 搜索结果DTO
 */
@Data
public class SearchResultDTO {
    /**
     * 店铺结果
     */
    private List<?> shops;
    
    /**
     * 博客结果
     */
    private List<?> blogs;
    
    /**
     * 推荐关键词
     */
    private List<String> suggestions;
    
    /**
     * 兼容旧字段：当前页结果数
     */
    private Long total;

    /**
     * 当前页结果数
     */
    private Long currentCount;

    /**
     * 总命中数
     */
    private Long totalHits;

    /**
     * 是否还有下一页
     */
    private Boolean hasMore;

    /**
     * 当前页码
     */
    private Integer currentPage;

    /**
     * 每页大小
     */
    private Integer pageSize;
    
    /**
     * 搜索耗时(ms)
     */
    private Long costTime;
}
