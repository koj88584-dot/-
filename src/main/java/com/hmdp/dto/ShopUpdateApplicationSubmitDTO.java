package com.hmdp.dto;

import lombok.Data;

@Data
public class ShopUpdateApplicationSubmitDTO {
    private String name;
    private Long typeId;
    private String area;
    private String address;
    private Double x;
    private Double y;
    private String images;
    private String proofImages;
    private String message;
}
