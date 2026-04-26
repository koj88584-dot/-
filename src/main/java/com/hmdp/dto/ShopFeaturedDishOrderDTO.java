package com.hmdp.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ShopFeaturedDishOrderDTO {
    private Long id;
    private Long dishId;
    private Long shopId;
    private String shopName;
    private Long userId;
    private String userNickName;
    private String dishName;
    private String dishImage;
    private String dishDescription;
    private Long payValue;
    private Integer status;
    private String statusText;
    private String verifyCode;
    private Integer commented;
    private LocalDateTime createTime;
    private LocalDateTime payTime;
    private LocalDateTime useTime;
    private LocalDateTime effectiveTime;
    private LocalDateTime expireTime;
}
