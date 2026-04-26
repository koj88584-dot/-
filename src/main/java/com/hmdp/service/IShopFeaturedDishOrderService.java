package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.dto.ShopFeaturedDishOrderVerifyDTO;
import com.hmdp.entity.ShopFeaturedDishOrder;

public interface IShopFeaturedDishOrderService extends IService<ShopFeaturedDishOrder> {
    Result createOrder(Long dishId);
    Result queryMyOrders(Integer status, Integer commented, Integer current);
    Result queryMerchantOrders(Long shopId, Integer status, Integer current);
    Result verifyByCode(ShopFeaturedDishOrderVerifyDTO dto);
    boolean hasVerifiedUncommentedOrder(Long userId, Long shopId);
    boolean markOneCommented(Long userId, Long shopId);
}
