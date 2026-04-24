package com.hmdp.dto;

import lombok.Data;

@Data
public class ShopClaimApplicationSubmitDTO {
    private Long shopId;
    private String proofImages;
    private String message;
}
