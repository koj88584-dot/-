package com.hmdp.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MerchantVoucherSaveDTO {
    private Long id;
    private Long shopId;
    private String title;
    private String subTitle;
    private String rules;
    private Long payValue;
    private Long actualValue;
    private Integer type;
    private Integer stock;
    private LocalDateTime beginTime;
    private LocalDateTime endTime;
    private Integer status;
}
