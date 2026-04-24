package com.hmdp.controller;

import com.hmdp.dto.CityOverviewDTO;
import com.hmdp.dto.CityProfileDTO;
import com.hmdp.dto.CityScenePackDTO;
import com.hmdp.dto.MerchantCityStatsDTO;
import com.hmdp.dto.Result;
import com.hmdp.service.ICityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/city")
public class CityController {

    @Resource
    private ICityService cityService;

    @GetMapping("/list")
    public Result listCities() {
        Map<String, Object> payload = new HashMap<>();
        List<CityOverviewDTO> cities = cityService.listCityOverviews();
        payload.put("cities", cities);
        payload.put("hotCities", cityService.listHotCityOverviews());
        payload.put("total", cities.size());
        payload.put("brandClaim", "懂这座城，才知道今天去哪");
        return Result.ok(payload);
    }

    @GetMapping("/{cityCode}")
    public Result getCityProfile(@PathVariable("cityCode") String cityCode) {
        CityProfileDTO profile = cityService.getCityProfile(cityCode);
        return Result.ok(profile);
    }

    @GetMapping("/{cityCode}/scenes")
    public Result getCityScenes(@PathVariable("cityCode") String cityCode) {
        List<CityScenePackDTO> scenes = cityService.listCityScenes(cityCode);
        return Result.ok(scenes);
    }

    @GetMapping("/{cityCode}/merchant-stats")
    public Result getMerchantCityStats(@PathVariable("cityCode") String cityCode) {
        MerchantCityStatsDTO stats = cityService.getMerchantCityStats(cityCode);
        return Result.ok(stats);
    }

    @PostMapping("/admin/{cityCode}/publish")
    public Result publishCityVersion(@PathVariable("cityCode") String cityCode) {
        return Result.ok(cityService.publishCityVersion(cityCode));
    }
}
