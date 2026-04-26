package com.hmdp.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GroupDealSaveDTO {
    private Long id;
    private Long shopId;
    private String title;
    private String description;
    private String images;
    private String rules;
    private Long price;
    private Long originalPrice;
    private Integer stock;
    private LocalDateTime beginTime;
    private LocalDateTime endTime;
}
