package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.GroupDealOrderVerifyDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.GroupDealOrder;

public interface IGroupDealOrderService extends IService<GroupDealOrder> {
    Result createOrder(Long dealId);
    Result queryMyOrders(Integer status, Integer commented, Integer current);
    Result queryMerchantOrders(Long shopId, Integer status, Integer current);
    Result verifyByCode(GroupDealOrderVerifyDTO dto);
    boolean hasVerifiedUncommentedOrder(Long userId, Long shopId);
    boolean markOneCommented(Long userId, Long shopId);
}
