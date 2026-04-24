package com.hmdp.service;

import com.hmdp.dto.CityOverviewDTO;
import com.hmdp.dto.CityProfileDTO;
import com.hmdp.dto.CityScenePackDTO;
import com.hmdp.dto.MerchantCityStatsDTO;

import java.util.List;
import java.util.Map;

public interface ICityService {

    List<CityOverviewDTO> listCityOverviews();

    List<CityOverviewDTO> listHotCityOverviews();

    CityProfileDTO getCityProfile(String cityCode);

    CityProfileDTO matchCityProfile(String cityCode, String cityName);

    List<CityScenePackDTO> listCityScenes(String cityCode);

    Map<String, Object> publishCityVersion(String cityCode);

    MerchantCityStatsDTO getMerchantCityStats(String cityCode);
}
