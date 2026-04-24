package com.hmdp.dto;

import lombok.Data;

@Data
public class ShopCreateApplicationSubmitDTO {
    private String shopName;
    private Long typeId;
    private String address;
    private Double x;
    private Double y;
    private String contactPhone;
    private String images;
    private String proofImages;
}
