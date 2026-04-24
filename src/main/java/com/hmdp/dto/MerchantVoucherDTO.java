package com.hmdp.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MerchantVoucherDTO {
    private Long id;
    private Long shopId;
    private String shopName;
    private String title;
    private String subTitle;
    private String rules;
    private Long payValue;
    private Long actualValue;
    private Integer type;
    private Integer status;
    private Integer stock;
    private Integer receivedCount;
    private Integer paidCount;
    private Integer verifiedCount;
    private LocalDateTime beginTime;
    private LocalDateTime endTime;
    private LocalDateTime createTime;
    private String statusText;
}
