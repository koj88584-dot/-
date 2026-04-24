package com.hmdp.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class VoucherOrderDTO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long voucherId;
    private Integer payType;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime payTime;
    private LocalDateTime useTime;
    private LocalDateTime refundTime;
    private LocalDateTime updateTime;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long shopId;
    private String shopName;
    private String voucherTitle;
    private String voucherSubTitle;
    private String voucherImages;
    private Long payValue;
    private Long actualValue;
    private Integer voucherType;
    private String verifyCode;
    private LocalDateTime effectiveTime;
    private LocalDateTime expireTime;
    private String statusText;
}
