package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.service.impl.MerchantAuthService;
import com.hmdp.service.impl.MerchantShopManagementService;
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

@RestController
@RequestMapping("/shop")
public class ShopController {

    @Resource
    public IShopService shopService;
    @Resource
    private MerchantAuthService merchantAuthService;
    @Resource
    private MerchantShopManagementService merchantShopManagementService;

    @GetMapping("/{id}")
    public Result queryShopById(@PathVariable("id") Long id) {
        return shopService.queryById(id);
    }

    @PostMapping
    public Result saveShop(@RequestBody Shop shop) {
        Result access = assertAdminWriteAccess();
        if (access != null) {
            return access;
        }
        shopService.save(shop);
        return Result.ok();
    }

    @PutMapping
    public Result updateShop(@RequestBody Shop shop) {
        Result access = assertAdminWriteAccess();
        if (access != null) {
            return access;
        }
        return shopService.update(shop);
    }

    @GetMapping("/{id}/featured-dishes")
    public Result queryFeaturedDishes(@PathVariable("id") Long id) {
        return Result.ok(merchantShopManagementService.listPublicDishes(id));
    }

    @GetMapping("/{id}/group-deals")
    public Result queryGroupDeals(@PathVariable("id") Long id) {
        return Result.ok(merchantShopManagementService.listPublicDeals(id));
    }

    @GetMapping("/of/type")
    public Result queryShopByType(
            @RequestParam("typeId") Integer typeId,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "x", required = false) Double x,
            @RequestParam(value = "y", required = false) Double y,
            @RequestParam(value = "cityCode", required = false) String cityCode
    ) {
        return shopService.queryShopByType(typeId, current, x, y, cityCode);
    }

    @GetMapping("/of/name")
    public Result queryShopByName(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "cityCode", required = false) String cityCode
    ) {
        return shopService.searchShopsByName(name, current, cityCode);
    }

    @GetMapping("/of/cache")
    public Result queryShopForAssociation(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "x", required = false) Double x,
            @RequestParam(value = "y", required = false) Double y,
            @RequestParam(value = "cityCode", required = false) String cityCode
    ) {
        return shopService.searchShopsForAssociation(name, x, y, cityCode);
    }

    private Result assertAdminWriteAccess() {
        if (UserHolder.getUser() == null || !merchantAuthService.isAdmin(UserHolder.getUser().getId())) {
            return Result.fail("店铺公开写接口已关闭，请使用商家资料变更审核流程");
        }
        return null;
    }
}
