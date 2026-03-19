package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.Voucher;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherService extends IService<Voucher> {

    Result queryVoucherOfShop(Long shopId);

    /**
     * 查询所有可用优惠券列表
     * @param current 当前页
     * @return 优惠券列表
     */
    Result queryAvailableVouchers(Integer current);

    void addSeckillVoucher(Voucher voucher);

    /**
     * 查询我的优惠券
     *
     * @param status  状态
     * @param current 当前页
     * @return {@link Result}
     */
    Result queryMyVouchers(Integer status, Integer current);

    /**
     * 查询优惠券详情
     *
     * @param id 优惠券id
     * @return {@link Result}
     */
    Result queryVoucherById(Long id);
}
