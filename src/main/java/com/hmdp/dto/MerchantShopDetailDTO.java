package com.hmdp.dto;

import com.hmdp.entity.Shop;
import lombok.Data;

@Data
public class MerchantShopDetailDTO {
    private Shop shop;
    private String memberRole;
    private Boolean canEdit;
    private Boolean canVerify;
    private Boolean pendingUpdate;
    private Long pendingUpdateApplicationId;
}
