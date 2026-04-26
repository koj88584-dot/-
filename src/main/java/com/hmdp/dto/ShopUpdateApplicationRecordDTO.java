package com.hmdp.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ShopUpdateApplicationRecordDTO {
    private Long id;
    private Long userId;
    private String userNickName;
    private Long shopId;
    private String shopName;
    private String changePayload;
    private String proofImages;
    private String message;
    private Integer status;
    private String statusText;
    private Long reviewerId;
    private String reviewerNickName;
    private String reviewRemark;
    private LocalDateTime reviewTime;
    private LocalDateTime createTime;
}
