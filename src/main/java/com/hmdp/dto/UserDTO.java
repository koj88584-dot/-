package com.hmdp.dto;

import lombok.Data;

@Data
public class UserDTO {
    private Long id;
    private String nickName;
    private String icon;
    private String primaryRole;
    private Boolean merchantEnabled;
    private Boolean admin;
    private String merchantApplicationStatus;
    private Long managedShopCount;
}
