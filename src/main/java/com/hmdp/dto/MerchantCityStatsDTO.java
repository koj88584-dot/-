package com.hmdp.dto;

import lombok.Data;

@Data
public class MerchantCityStatsDTO {

    private String cityCode;

    private String cityName;

    private Integer shopCount;

    private Integer voucherCount;

    private Integer activeVoucherCount;

    private Integer orderCount;

    private Integer paidOrderCount;

    private Integer verifiedOrderCount;

    private Long grossPayValue;
}
