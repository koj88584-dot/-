package com.hmdp.dto;

import lombok.Data;

import java.util.List;

@Data
public class ShopDecisionCardDTO {

    private Long shopId;

    private String shopName;

    private String cityCode;

    private String cityName;

    private String district;

    private String businessArea;

    private List<String> sceneTags;

    private Long couponPrice;

    private Double distance;

    private String decisionReason;

    private Integer score;
}
