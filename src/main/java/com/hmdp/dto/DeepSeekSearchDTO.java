package com.hmdp.dto;

import lombok.Data;

/**
 * DeepSeek搜索请求DTO
 */
@Data
public class DeepSeekSearchDTO {
    /**
     * 搜索关键词
     */
    private String keyword;
    
    /**
     * 搜索类型：shop-店铺 blog-博客 all-全部
     */
    private String type = "all";
    
    /**
     * 排序方式：relevance-相关度 distance-距离 rating-评分 price-价格
     */
    private String sortBy = "relevance";
    
    /**
     * 用户经度（用于距离排序）
     */
    private Double longitude;
    
    /**
     * 用户纬度（用于距离排序）
     */
    private Double latitude;
    
    /**
     * 价格范围-最低
     */
    private Long minPrice;
    
    /**
     * 价格范围-最高
     */
    private Long maxPrice;
    
    /**
     * 店铺类型id
     */
    private Long typeId;
    
    /**
     * 当前页
     */
    private Integer current = 1;
}
