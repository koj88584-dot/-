package com.hmdp.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GroupDealOrderDTO {
    private Long id;
    private Long dealId;
    private Long shopId;
    private String shopName;
    private Long userId;
    private String userNickName;
    private String dealTitle;
    private String dealImages;
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
