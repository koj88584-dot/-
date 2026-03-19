package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.LimitType;
import com.hmdp.utils.RateLimiter;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {
    @Resource
    private IVoucherOrderService  voucherOrderService;

    /**
     * 秒杀下单
     */
    @PostMapping("seckill/{id}")
    @RateLimiter(key = "seckill", time = 1, count = 1, limitType = LimitType.USER, message = "操作太频繁，请稍后再试")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.seckillVoucher(voucherId);
    }

    /**
     * 查询我的订单列表
     * @param status 订单状态：1未支付 2已支付 3已核销 4已取消 5退款中 6已退款，不传则查全部
     * @param current 当前页
     */
    @GetMapping("/list")
    public Result queryMyOrders(@RequestParam(value = "status", required = false) Integer status,
                                @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return voucherOrderService.queryMyOrders(status, current);
    }

    /**
     * 查询订单详情
     * @param id 订单id
     */
    @GetMapping("/{id}")
    public Result queryOrderById(@PathVariable("id") Long id) {
        return voucherOrderService.queryOrderById(id);
    }

    /**
     * 核销订单（商家使用）
     * @param id 订单id
     */
    @PostMapping("/verify/{id}")
    public Result verifyOrder(@PathVariable("id") Long id) {
        return voucherOrderService.verifyOrder(id);
    }

    /**
     * 取消订单
     * @param id 订单id
     */
    @PostMapping("/cancel/{id}")
    public Result cancelOrder(@PathVariable("id") Long id) {
        return voucherOrderService.cancelOrder(id);
    }

    /**
     * 支付订单（模拟支付）
     * @param id 订单id
     */
    @PostMapping("/pay/{id}")
    public Result payOrder(@PathVariable("id") Long id) {
        return voucherOrderService.payOrder(id);
    }

    /**
     * 申请退款
     * @param id 订单id
     */
    @PostMapping("/refund/{id}")
    public Result refundOrder(@PathVariable("id") Long id) {
        return voucherOrderService.refundOrder(id);
    }

    /**
     * 领取普通优惠券
     * @param id 优惠券id
     */
    @PostMapping("/receive/{id}")
    @RateLimiter(key = "receive", time = 1, count = 1, limitType = LimitType.USER, message = "操作太频繁，请稍后再试")
    public Result receiveVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.receiveVoucher(voucherId);
    }
}
