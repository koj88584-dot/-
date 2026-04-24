package com.hmdp.controller;

import com.hmdp.dto.MerchantProfileDTO;
import com.hmdp.dto.MerchantVoucherSaveDTO;
import com.hmdp.dto.MerchantVoucherVerifyDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.Voucher;
import com.hmdp.service.IShopService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.IVoucherService;
import com.hmdp.service.impl.MerchantAuthService;
import com.hmdp.utils.UserHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/merchant")
public class MerchantVoucherController {

    @Resource
    private IVoucherService voucherService;
    @Resource
    private IVoucherOrderService voucherOrderService;
    @Resource
    private IShopService shopService;
    @Resource
    private MerchantAuthService merchantAuthService;

    @GetMapping("/me")
    public Result queryMerchantProfile() {
        MerchantProfileDTO profile = merchantAuthService.buildProfile(UserHolder.getUser().getId());
        return Result.ok(profile);
    }

    @GetMapping("/shops")
    public Result queryMerchantShops(@RequestParam(value = "keyword", required = false) String keyword,
                                     @RequestParam(value = "current", defaultValue = "1") Integer current) {
        Long userId = UserHolder.getUser().getId();
        MerchantProfileDTO profile = merchantAuthService.buildProfile(userId);
        if (!Boolean.TRUE.equals(profile.getMerchantEnabled())) {
            return Result.fail("暂无商家权限");
        }
        List<Shop> shops = merchantAuthService.listManagedShops(userId, keyword, current);
        return Result.ok(shops, (long) shops.size());
    }

    @GetMapping("/vouchers")
    public Result queryMerchantVouchers(@RequestParam("shopId") Long shopId,
                                        @RequestParam(value = "status", required = false) Integer status) {
        Result access = assertCanManageShop(shopId);
        if (access != null) {
            return access;
        }
        return voucherService.queryMerchantVouchers(shopId, status);
    }

    @PostMapping("/vouchers")
    public Result saveMerchantVoucher(@RequestBody MerchantVoucherSaveDTO dto) {
        Result access = assertCanManageShop(dto == null ? null : dto.getShopId());
        if (access != null) {
            return access;
        }
        try {
            return voucherService.saveMerchantVoucher(dto);
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
    }

    @PutMapping("/vouchers/{id}")
    public Result updateMerchantVoucher(@PathVariable("id") Long id, @RequestBody MerchantVoucherSaveDTO dto) {
        Voucher voucher = voucherService.getById(id);
        if (voucher == null) {
            return Result.fail("优惠券不存在");
        }
        Result access = assertCanManageShop(voucher.getShopId());
        if (access != null) {
            return access;
        }
        try {
            return voucherService.updateMerchantVoucher(id, dto);
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
    }

    @PostMapping("/vouchers/{id}/publish")
    public Result publishVoucher(@PathVariable("id") Long id) {
        Voucher voucher = voucherService.getById(id);
        if (voucher == null) {
            return Result.fail("优惠券不存在");
        }
        Result access = assertCanManageShop(voucher.getShopId());
        if (access != null) {
            return access;
        }
        try {
            return voucherService.publishVoucher(id);
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
    }

    @PostMapping("/vouchers/{id}/unpublish")
    public Result unpublishVoucher(@PathVariable("id") Long id) {
        Voucher voucher = voucherService.getById(id);
        if (voucher == null) {
            return Result.fail("优惠券不存在");
        }
        Result access = assertCanManageShop(voucher.getShopId());
        if (access != null) {
            return access;
        }
        return voucherService.unpublishVoucher(id);
    }

    @GetMapping("/voucher-orders")
    public Result queryMerchantOrders(@RequestParam("shopId") Long shopId,
                                      @RequestParam(value = "status", required = false) Integer status,
                                      @RequestParam(value = "current", defaultValue = "1") Integer current) {
        Result access = assertCanManageShop(shopId);
        if (access != null) {
            return access;
        }
        return voucherOrderService.queryMerchantOrders(shopId, status, current);
    }

    @PostMapping("/voucher-orders/verify-code")
    public Result verifyByCode(@RequestBody MerchantVoucherVerifyDTO dto) {
        Long shopId = dto == null ? null : dto.getShopId();
        Result access = assertCanManageShop(shopId);
        if (access != null) {
            return access;
        }
        return voucherOrderService.verifyOrderByCode(shopId, dto.getVerifyCode());
    }

    private Result assertCanManageShop(Long shopId) {
        if (shopId == null) {
            return Result.fail("店铺不能为空");
        }
        if (!merchantAuthService.canManageShop(UserHolder.getUser().getId(), shopId)) {
            Shop shop = shopService.getById(shopId);
            if (shop == null) {
                return Result.fail("店铺不存在");
            }
            return Result.fail("无权管理该店铺");
        }
        return null;
    }
}
