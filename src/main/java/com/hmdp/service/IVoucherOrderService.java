package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import org.jetbrains.annotations.NotNull;
import org.springframework.transaction.annotation.Transactional;

/**
 * ivoucher订单服务
 * <p>
 * 服务类
 * </p>
 *
 * @author 虎哥
 * @date 2022/10/09
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    /**
     * 秒杀优惠券
     *
     * @param voucherId 券id
     * @return {@link Result}
     */
    Result seckillVoucher(Long voucherId);

    /**
     * 创建优惠券订单
     *
     * @param voucherOrder 券订单
     */
    @NotNull
    @Transactional(rollbackFor = Exception.class)
    void createVoucherOrder(VoucherOrder voucherOrder);

    /**
     * 查询我的订单列表
     *
     * @param status  订单状态
     * @param current 当前页
     * @return {@link Result}
     */
    Result queryMyOrders(Integer status, Integer current);

    /**
     * 查询订单详情
     *
     * @param id 订单id
     * @return {@link Result}
     */
    Result queryOrderById(Long id);

    /**
     * 核销订单
     *
     * @param id 订单id
     * @return {@link Result}
     */
    Result verifyOrder(Long id);

    /**
     * 取消订单
     *
     * @param id 订单id
     * @return {@link Result}
     */
    Result cancelOrder(Long id);

    /**
     * 支付订单
     *
     * @param id 订单id
     * @return {@link Result}
     */
    Result payOrder(Long id);

    /**
     * 申请退款
     *
     * @param id 订单id
     * @return {@link Result}
     */
    Result refundOrder(Long id);

    /**
     * 领取普通优惠券
     *
     * @param voucherId 优惠券id
     * @return {@link Result}
     */
    Result receiveVoucher(Long voucherId);
}
