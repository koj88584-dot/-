package com.hmdp.dto;

import lombok.Data;

@Data
public class RegisterFormDTO {
    private String phone;
    private String code;
    private String password;
    private String confirmPassword;
    private String nickName;
}
