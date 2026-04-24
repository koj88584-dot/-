package com.hmdp.dto;

import lombok.Data;

import java.util.List;

@Data
public class CityOverviewDTO {

    private String cityCode;

    private String cityName;

    private String province;

    private String heroTitle;

    private String cityTagline;

    private List<String> cultureTags;

    private List<String> featuredDistricts;

    private List<String> primaryCategories;

    private List<String> hotSearches;

    private String priceTone;

    private Boolean open;
}
