package com.hmdp.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ShopClaimApplicationRecordDTO {
    private Long id;
    private Long userId;
    private String userNickName;
    private Long shopId;
    private String shopName;
    private Long merchantApplicationId;
    private String proofImages;
    private String message;
    private Integer status;
    private String statusText;
    private Long reviewerId;
    private String reviewerName;
    private String reviewRemark;
    private LocalDateTime reviewTime;
    private LocalDateTime createTime;
}
