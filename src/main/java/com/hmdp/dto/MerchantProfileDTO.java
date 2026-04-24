package com.hmdp.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MerchantProfileDTO {
    private Boolean merchantEnabled = false;
    private Boolean admin = false;
    private String primaryRole = "USER";
    private List<String> roles = new ArrayList<>();
    private Long managedShopCount = 0L;
    private String merchantApplicationStatus = "NONE";
}
