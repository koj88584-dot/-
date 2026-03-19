package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.UserHolder;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.SECKILL_STOCK_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    @Lazy
    private IVoucherOrderService voucherOrderService;

    @Override
    public Result queryVoucherOfShop(Long shopId) {
        // 查询优惠券信息
        List<Voucher> vouchers = baseMapper.queryVoucherOfShop(shopId);
        // 返回结果
        return Result.ok(vouchers);
    }

    @Override
    public Result queryAvailableVouchers(Integer current) {
        // 查询所有状态正常的优惠券
        Page<Voucher> page = lambdaQuery()
                .eq(Voucher::getStatus, 1)
                .orderByDesc(Voucher::getCreateTime)
                .page(new Page<>(current, 10));
        
        List<Voucher> vouchers = page.getRecords();
        
        // 为秒杀券填充库存和时间信息
        for (Voucher voucher : vouchers) {
            if (voucher.getType() == 1) {
                SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucher.getId());
                if (seckillVoucher != null) {
                    voucher.setStock(seckillVoucher.getStock());
                    voucher.setBeginTime(seckillVoucher.getBeginTime());
                    voucher.setEndTime(seckillVoucher.getEndTime());
                }
            }
        }
        
        return Result.ok(vouchers);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addSeckillVoucher(Voucher voucher) {
        // 保存优惠券
        save(voucher);
        // 保存秒杀信息
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());
        seckillVoucherService.save(seckillVoucher);
        //保存秒杀库存的到redis
        stringRedisTemplate.opsForValue().set(SECKILL_STOCK_KEY+voucher.getId(),voucher.getStock().toString());
    }

    @Override
    public Result queryMyVouchers(Integer status, Integer current) {
        Long userId = UserHolder.getUser().getId();
        
        // 查询用户的订单
        Page<VoucherOrder> orderPage = voucherOrderService.lambdaQuery()
                .eq(VoucherOrder::getUserId, userId)
                .eq(status != null, VoucherOrder::getStatus, status)
                .orderByDesc(VoucherOrder::getCreateTime)
                .page(new Page<>(current, 10));
        
        List<VoucherOrder> orders = orderPage.getRecords();
        if (orders.isEmpty()) {
            return Result.ok(new ArrayList<>());
        }
        
        // 获取优惠券ID列表
        List<Long> voucherIds = orders.stream()
                .map(VoucherOrder::getVoucherId)
                .distinct()
                .collect(Collectors.toList());
        
        // 批量查询优惠券详情
        List<Voucher> vouchers = listByIds(voucherIds);
        
        return Result.ok(vouchers);
    }

    @Override
    public Result queryVoucherById(Long id) {
        Voucher voucher = getById(id);
        if (voucher == null) {
            return Result.fail("优惠券不存在");
        }
        
        // 如果是秒杀券，查询秒杀信息
        if (voucher.getType() == 1) {
            SeckillVoucher seckillVoucher = seckillVoucherService.getById(id);
            if (seckillVoucher != null) {
                voucher.setStock(seckillVoucher.getStock());
                voucher.setBeginTime(seckillVoucher.getBeginTime());
                voucher.setEndTime(seckillVoucher.getEndTime());
            }
        }
        
        return Result.ok(voucher);
    }
}
