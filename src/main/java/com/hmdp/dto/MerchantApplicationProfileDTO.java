package com.hmdp.dto;

import lombok.Data;

@Data
public class MerchantApplicationProfileDTO {
    private Boolean merchantEnabled = false;
    private Boolean admin = false;
    private String primaryRole = "USER";
    private String merchantApplicationStatus = "NONE";
    private String merchantApplicationStatusText = "未提交";
    private Long managedShopCount = 0L;
    private MerchantApplicationRecordDTO latestMerchantApplication;
    private ShopClaimApplicationRecordDTO latestShopClaimApplication;
    private ShopCreateApplicationRecordDTO latestShopCreateApplication;
}
