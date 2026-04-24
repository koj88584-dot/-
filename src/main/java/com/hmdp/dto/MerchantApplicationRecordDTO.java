package com.hmdp.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MerchantApplicationRecordDTO {
    private Long id;
    private Long userId;
    private String userNickName;
    private String contactName;
    private String contactPhone;
    private String companyName;
    private String description;
    private String proofImages;
    private Integer status;
    private String statusText;
    private Long reviewerId;
    private String reviewerName;
    private String reviewRemark;
    private LocalDateTime reviewTime;
    private LocalDateTime createTime;
}
