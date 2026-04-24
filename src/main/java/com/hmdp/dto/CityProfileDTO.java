package com.hmdp.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class CityProfileDTO {

    private String cityCode;

    private String cityName;

    private String province;

    private Double longitude;

    private Double latitude;

    private String heroTitle;

    private String cityTagline;

    private List<String> cultureTags;

    private List<String> defaultScenes;

    private List<String> primaryCategories;

    private String priceTone;

    private Map<String, String> visualTheme;

    private List<String> featuredDistricts;

    private List<String> seasonalHooks;

    private List<String> hotSearches;

    private List<String> featuredRoutes;

    private Boolean open;
}
