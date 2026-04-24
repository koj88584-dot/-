package com.hmdp.dto;

import lombok.Data;

@Data
public class MerchantVoucherVerifyDTO {
    private Long shopId;
    private String verifyCode;
}
