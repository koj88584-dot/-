package com.hmdp.controller;

import com.hmdp.dto.MerchantApplicationProfileDTO;
import com.hmdp.dto.MerchantApplicationSubmitDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.ShopClaimApplicationSubmitDTO;
import com.hmdp.dto.ShopCreateApplicationSubmitDTO;
import com.hmdp.service.impl.MerchantApplicationFlowService;
import com.hmdp.utils.UserHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/merchant/application")
public class MerchantApplicationController {

    @Resource
    private MerchantApplicationFlowService merchantApplicationFlowService;

    @GetMapping("/me")
    public Result queryMyMerchantApplicationProfile() {
        Long userId = UserHolder.getUser().getId();
        MerchantApplicationProfileDTO profile = merchantApplicationFlowService.buildApplicationProfile(userId);
        return Result.ok(profile);
    }

    @PostMapping
    public Result submitMerchantApplication(@RequestBody MerchantApplicationSubmitDTO dto) {
        return merchantApplicationFlowService.submitMerchantApplication(UserHolder.getUser().getId(), dto);
    }

    @PostMapping("/claim-shop")
    public Result submitClaimShopApplication(@RequestBody ShopClaimApplicationSubmitDTO dto) {
        return merchantApplicationFlowService.submitShopClaim(UserHolder.getUser().getId(), dto);
    }

    @PostMapping("/create-shop")
    public Result submitCreateShopApplication(@RequestBody ShopCreateApplicationSubmitDTO dto) {
        return merchantApplicationFlowService.submitShopCreate(UserHolder.getUser().getId(), dto);
    }

    @GetMapping("/shops")
    public Result searchClaimableShops(@RequestParam(value = "keyword", required = false) String keyword) {
        return Result.ok(merchantApplicationFlowService.searchClaimableShops(keyword));
    }
}
