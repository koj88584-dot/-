package com.hmdp.dto;

import lombok.Data;

@Data
public class LocationContextDTO {

    private Double longitude;

    private Double latitude;

    private Integer accuracy;

    private String source;

    private String provider;

    private String province;

    private String city;

    private String district;

    private String adcode;

    private String cityCode;

    private String formattedAddress;

    private Boolean amapAvailable;

    private String confidence;

    private Boolean cityEditionEnabled;

    private CityProfileDTO cityProfile;
}
