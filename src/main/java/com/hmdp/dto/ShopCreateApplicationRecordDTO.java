package com.hmdp.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ShopCreateApplicationRecordDTO {
    private Long id;
    private Long userId;
    private String userNickName;
    private Long merchantApplicationId;
    private String shopName;
    private Long typeId;
    private String typeName;
    private String address;
    private Double x;
    private Double y;
    private String contactPhone;
    private String images;
    private String proofImages;
    private Integer status;
    private String statusText;
    private Long reviewerId;
    private String reviewerName;
    private String reviewRemark;
    private LocalDateTime reviewTime;
    private LocalDateTime createTime;
}
