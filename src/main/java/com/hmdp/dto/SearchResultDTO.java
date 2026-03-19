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
     * 总数
     */
    private Long total;
    
    /**
     * 搜索耗时(ms)
     */
    private Long costTime;
}
