package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.service.ICityService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/admin/city")
public class AdminCityController {

    @Resource
    private ICityService cityService;

    @PostMapping("/{cityCode}/publish")
    public Result publishCityVersion(@PathVariable("cityCode") String cityCode) {
        return Result.ok(cityService.publishCityVersion(cityCode));
    }
}
