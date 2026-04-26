package com.hmdp.controller;

import com.hmdp.dto.FeaturedDishSaveDTO;
import com.hmdp.dto.GroupDealOrderVerifyDTO;
import com.hmdp.dto.GroupDealSaveDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.ShopFeaturedDishOrderVerifyDTO;
import com.hmdp.dto.ShopQuickInfoDTO;
import com.hmdp.dto.ShopUpdateApplicationSubmitDTO;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopFeaturedDish;
import com.hmdp.service.IGroupDealOrderService;
import com.hmdp.service.IShopFeaturedDishOrderService;
import com.hmdp.service.IShopFeaturedDishService;
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
@RequestMapping("/merchant")
public class MerchantShopController {

    @Resource
    private IShopService shopService;
    @Resource
    private IShopFeaturedDishService shopFeaturedDishService;
    @Resource
    private IShopFeaturedDishOrderService shopFeaturedDishOrderService;
    @Resource
    private IGroupDealOrderService groupDealOrderService;
    @Resource
    private MerchantAuthService merchantAuthService;
    @Resource
    private MerchantShopManagementService merchantShopManagementService;

    @GetMapping("/shops/{shopId}")
    public Result queryMerchantShopDetail(@PathVariable("shopId") Long shopId) {
        Long userId = UserHolder.getUser().getId();
        Result access = assertCanManageShop(userId, shopId);
        if (access != null) {
            return access;
        }
        return Result.ok(merchantShopManagementService.buildMerchantShopDetail(userId, shopId));
    }

    @PutMapping("/shops/{shopId}/quick-info")
    public Result updateQuickInfo(@PathVariable("shopId") Long shopId, @RequestBody ShopQuickInfoDTO dto) {
        Long userId = UserHolder.getUser().getId();
        Result access = assertCanEditShop(userId, shopId);
        if (access != null) {
            return access;
        }
        return merchantShopManagementService.updateQuickInfo(shopId, dto);
    }

    @PostMapping("/shops/{shopId}/update-applications")
    public Result submitUpdateApplication(@PathVariable("shopId") Long shopId, @RequestBody ShopUpdateApplicationSubmitDTO dto) {
        Long userId = UserHolder.getUser().getId();
        Result access = assertCanEditShop(userId, shopId);
        if (access != null) {
            return access;
        }
        return merchantShopManagementService.submitUpdateApplication(userId, shopId, dto);
    }

    @GetMapping("/shops/{shopId}/featured-dishes")
    public Result listMerchantDishes(@PathVariable("shopId") Long shopId) {
        Long userId = UserHolder.getUser().getId();
        Result access = assertCanEditShop(userId, shopId);
        if (access != null) {
            return access;
        }
        return Result.ok(merchantShopManagementService.listMerchantDishes(shopId));
    }

    @PostMapping("/shops/{shopId}/featured-dishes")
    public Result createDish(@PathVariable("shopId") Long shopId, @RequestBody FeaturedDishSaveDTO dto) {
        Long userId = UserHolder.getUser().getId();
        Result access = assertCanEditShop(userId, shopId);
        if (access != null) {
            return access;
        }
        return merchantShopManagementService.saveDish(shopId, dto);
    }

    @PutMapping("/shops/{shopId}/featured-dishes")
    public Result updateDish(@PathVariable("shopId") Long shopId, @RequestBody FeaturedDishSaveDTO dto) {
        Long userId = UserHolder.getUser().getId();
        Result access = assertCanEditShop(userId, shopId);
        if (access != null) {
            return access;
        }
        return merchantShopManagementService.saveDish(shopId, dto);
    }

    @PostMapping("/featured-dishes/{id}/publish")
    public Result publishDish(@PathVariable("id") Long id) {
        Result access = assertCanEditDish(id);
        if (access != null) {
            return access;
        }
        return merchantShopManagementService.publishDish(id);
    }

    @PostMapping("/featured-dishes/{id}/unpublish")
    public Result unpublishDish(@PathVariable("id") Long id) {
        Result access = assertCanEditDish(id);
        if (access != null) {
            return access;
        }
        return merchantShopManagementService.unpublishDish(id);
    }

    @GetMapping("/shops/{shopId}/group-deals")
    public Result listMerchantDeals(@PathVariable("shopId") Long shopId) {
        Long userId = UserHolder.getUser().getId();
        Result access = assertCanEditShop(userId, shopId);
        if (access != null) {
            return access;
        }
        return Result.ok(merchantShopManagementService.listMerchantDeals(shopId));
    }

    @PostMapping("/shops/{shopId}/group-deals")
    public Result createGroupDeal(@PathVariable("shopId") Long shopId, @RequestBody GroupDealSaveDTO dto) {
        Long userId = UserHolder.getUser().getId();
        Result access = assertCanEditShop(userId, shopId);
        if (access != null) {
            return access;
        }
        return merchantShopManagementService.saveDeal(shopId, dto);
    }

    @PutMapping("/shops/{shopId}/group-deals")
    public Result updateGroupDeal(@PathVariable("shopId") Long shopId, @RequestBody GroupDealSaveDTO dto) {
        Long userId = UserHolder.getUser().getId();
        Result access = assertCanEditShop(userId, shopId);
        if (access != null) {
            return access;
        }
        return merchantShopManagementService.saveDeal(shopId, dto);
    }

    @PostMapping("/group-deals/{id}/publish")
    public Result publishGroupDeal(@PathVariable("id") Long id) {
        Long userId = UserHolder.getUser().getId();
        if (!merchantShopManagementService.isDealOfEditableShop(id, userId)) {
            return Result.fail("无权管理该团购");
        }
        return merchantShopManagementService.publishDeal(id);
    }

    @PostMapping("/group-deals/{id}/unpublish")
    public Result unpublishGroupDeal(@PathVariable("id") Long id) {
        Long userId = UserHolder.getUser().getId();
        if (!merchantShopManagementService.isDealOfEditableShop(id, userId)) {
            return Result.fail("无权管理该团购");
        }
        return merchantShopManagementService.unpublishDeal(id);
    }

    @GetMapping("/group-deal-orders")
    public Result queryGroupDealOrders(@RequestParam("shopId") Long shopId,
                                       @RequestParam(value = "status", required = false) Integer status,
                                       @RequestParam(value = "current", defaultValue = "1") Integer current) {
        Long userId = UserHolder.getUser().getId();
        Result access = assertCanVerifyShop(userId, shopId);
        if (access != null) {
            return access;
        }
        return groupDealOrderService.queryMerchantOrders(shopId, status, current);
    }

    @PostMapping("/group-deal-orders/verify-code")
    public Result verifyGroupDealOrder(@RequestBody GroupDealOrderVerifyDTO dto) {
        Long shopId = dto == null ? null : dto.getShopId();
        Long userId = UserHolder.getUser().getId();
        Result access = assertCanVerifyShop(userId, shopId);
        if (access != null) {
            return access;
        }
        return groupDealOrderService.verifyByCode(dto);
    }

    @GetMapping("/featured-dish-orders")
    public Result queryFeaturedDishOrders(@RequestParam("shopId") Long shopId,
                                          @RequestParam(value = "status", required = false) Integer status,
                                          @RequestParam(value = "current", defaultValue = "1") Integer current) {
        Long userId = UserHolder.getUser().getId();
        Result access = assertCanVerifyShop(userId, shopId);
        if (access != null) {
            return access;
        }
        return shopFeaturedDishOrderService.queryMerchantOrders(shopId, status, current);
    }

    @PostMapping("/featured-dish-orders/verify-code")
    public Result verifyFeaturedDishOrder(@RequestBody ShopFeaturedDishOrderVerifyDTO dto) {
        Long shopId = dto == null ? null : dto.getShopId();
        Long userId = UserHolder.getUser().getId();
        Result access = assertCanVerifyShop(userId, shopId);
        if (access != null) {
            return access;
        }
        return shopFeaturedDishOrderService.verifyByCode(dto);
    }

    private Result assertCanManageShop(Long userId, Long shopId) {
        if (shopId == null) {
            return Result.fail("店铺不能为空");
        }
        if (!merchantAuthService.canManageShop(userId, shopId)) {
            Shop shop = shopService.getById(shopId);
            return shop == null ? Result.fail("店铺不存在") : Result.fail("无权管理该店铺");
        }
        return null;
    }

    private Result assertCanEditShop(Long userId, Long shopId) {
        if (shopId == null) {
            return Result.fail("店铺不能为空");
        }
        if (!merchantAuthService.canEditShop(userId, shopId)) {
            Shop shop = shopService.getById(shopId);
            return shop == null ? Result.fail("店铺不存在") : Result.fail("只有店铺负责人或管理员可以修改资料");
        }
        return null;
    }

    private Result assertCanVerifyShop(Long userId, Long shopId) {
        if (shopId == null) {
            return Result.fail("店铺不能为空");
        }
        if (!merchantAuthService.canVerifyShopOrder(userId, shopId)) {
            Shop shop = shopService.getById(shopId);
            return shop == null ? Result.fail("店铺不存在") : Result.fail("无权核销该店铺订单");
        }
        return null;
    }

    private Result assertCanEditDish(Long dishId) {
        ShopFeaturedDish dish = shopFeaturedDishService.getById(dishId);
        if (dish == null) {
            return Result.fail("招牌菜不存在");
        }
        return assertCanEditShop(UserHolder.getUser().getId(), dish.getShopId());
    }
}
