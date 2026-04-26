package com.hmdp.dto;

import lombok.Data;

@Data
public class FeaturedDishSaveDTO {
    private Long id;
    private Long shopId;
    private String name;
    private String description;
    private String image;
    private Long price;
    private Integer sort;
}
