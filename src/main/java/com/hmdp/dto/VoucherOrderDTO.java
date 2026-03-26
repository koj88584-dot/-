package com.hmdp.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class VoucherOrderDTO {
    private Long id;
    private Long userId;
    private Long voucherId;
    private Integer payType;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime payTime;
    private LocalDateTime useTime;
    private LocalDateTime refundTime;
    private LocalDateTime updateTime;

    private Long shopId;
    private String shopName;
    private String voucherTitle;
    private String voucherSubTitle;
    private String voucherImages;
    private Long payValue;
    private Long actualValue;
    private Integer voucherType;
}
