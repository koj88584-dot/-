package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.MerchantVoucherDTO;
import com.hmdp.dto.MerchantVoucherSaveDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Shop;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IShopService;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.SECKILL_STOCK_KEY;

@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    @Lazy
    private IVoucherOrderService voucherOrderService;
    @Resource
    private IShopService shopService;

    @Override
    public Result queryVoucherOfShop(Long shopId) {
        List<Voucher> vouchers = lambdaQuery()
                .eq(Voucher::getShopId, shopId)
                .eq(Voucher::getStatus, VoucherViewSupport.STATUS_PUBLISHED)
                .orderByDesc(Voucher::getCreateTime)
                .list();
        fillVoucherRuntimeFields(vouchers);
        expireIfNeeded(vouchers);
        List<Voucher> visible = vouchers.stream()
                .filter(voucher -> Objects.equals(voucher.getStatus(), VoucherViewSupport.STATUS_PUBLISHED))
                .collect(Collectors.toList());
        fillVoucherShopInfo(visible);
        return Result.ok(visible);
    }

    @Override
    public Result queryAvailableVouchers(Integer current) {
        int pageNo = current == null || current < 1 ? 1 : current;
        LocalDateTime now = LocalDateTime.now();
        Page<Voucher> page = lambdaQuery()
                .eq(Voucher::getStatus, VoucherViewSupport.STATUS_PUBLISHED)
                .and(wrapper -> wrapper.isNull(Voucher::getEndTime).or().ge(Voucher::getEndTime, now))
                .orderByDesc(Voucher::getCreateTime)
                .page(new Page<>(pageNo, 10));
        List<Voucher> vouchers = page.getRecords();
        fillVoucherRuntimeFields(vouchers);
        expireIfNeeded(vouchers);
        List<Voucher> visible = vouchers.stream()
                .filter(voucher -> Objects.equals(voucher.getStatus(), VoucherViewSupport.STATUS_PUBLISHED))
                .collect(Collectors.toList());
        fillVoucherShopInfo(visible);
        return Result.ok(visible);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addSeckillVoucher(Voucher voucher) {
        Voucher entity = BeanUtil.copyProperties(voucher, Voucher.class);
        entity.setStatus(entity.getStatus() == null ? VoucherViewSupport.STATUS_PUBLISHED : entity.getStatus());
        validateVoucher(entity);
        save(entity);
        saveOrUpdateSeckillVoucher(entity);
    }

    @Override
    public Result queryMyVouchers(Integer status, Integer current) {
        Long userId = UserHolder.getUser().getId();

        Page<VoucherOrder> orderPage = voucherOrderService.lambdaQuery()
                .eq(VoucherOrder::getUserId, userId)
                .eq(status != null, VoucherOrder::getStatus, status)
                .orderByDesc(VoucherOrder::getCreateTime)
                .page(new Page<>(current, 10));

        List<VoucherOrder> orders = orderPage.getRecords();
        if (orders.isEmpty()) {
            return Result.ok(new ArrayList<>());
        }

        List<Long> voucherIds = orders.stream()
                .map(VoucherOrder::getVoucherId)
                .distinct()
                .collect(Collectors.toList());

        List<Voucher> vouchers = listByIds(voucherIds);
        fillVoucherRuntimeFields(vouchers);
        return Result.ok(vouchers);
    }

    @Override
    public Result queryVoucherById(Long id) {
        Voucher voucher = getById(id);
        if (voucher == null) {
            return Result.fail("优惠券不存在");
        }
        fillVoucherRuntimeField(voucher);
        expireIfNeeded(Collections.singletonList(voucher));
        return Result.ok(voucher);
    }

    @Override
    public Result queryMerchantVouchers(Long shopId, Integer status) {
        if (shopId == null) {
            return Result.fail("店铺不能为空");
        }
        Shop shop = shopService.getById(shopId);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        List<Voucher> vouchers = lambdaQuery()
                .eq(Voucher::getShopId, shopId)
                .eq(status != null, Voucher::getStatus, status)
                .orderByDesc(Voucher::getCreateTime)
                .list();
        fillVoucherRuntimeFields(vouchers);
        expireIfNeeded(vouchers);

        Map<Long, List<VoucherOrder>> orderMap = loadOrdersByVoucher(vouchers);
        List<MerchantVoucherDTO> data = vouchers.stream().map(voucher -> {
            MerchantVoucherDTO dto = BeanUtil.copyProperties(voucher, MerchantVoucherDTO.class);
            dto.setShopName(shop.getName());
            List<VoucherOrder> orders = orderMap.getOrDefault(voucher.getId(), Collections.emptyList());
            dto.setReceivedCount(orders.size());
            dto.setPaidCount((int) orders.stream()
                    .filter(order -> Objects.equals(order.getStatus(), VoucherOrderFlowSupport.STATUS_PAID)
                            || Objects.equals(order.getStatus(), VoucherOrderFlowSupport.STATUS_VERIFIED)
                            || Objects.equals(order.getStatus(), VoucherOrderFlowSupport.STATUS_REFUNDED))
                    .count());
            dto.setVerifiedCount((int) orders.stream()
                    .filter(order -> Objects.equals(order.getStatus(), VoucherOrderFlowSupport.STATUS_VERIFIED))
                    .count());
            VoucherViewSupport.fillStatusText(dto);
            return dto;
        }).collect(Collectors.toList());
        return Result.ok(data);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result saveMerchantVoucher(MerchantVoucherSaveDTO dto) {
        Voucher voucher = buildVoucher(dto);
        voucher.setStatus(dto.getStatus() == null ? VoucherViewSupport.STATUS_DRAFT : dto.getStatus());
        validateVoucher(voucher);
        save(voucher);
        if (voucher.getType() != null && voucher.getType() == 1) {
            saveOrUpdateSeckillVoucher(voucher);
        }
        return Result.ok(voucher.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result updateMerchantVoucher(Long id, MerchantVoucherSaveDTO dto) {
        Voucher existing = getById(id);
        if (existing == null) {
            return Result.fail("优惠券不存在");
        }
        if (Objects.equals(existing.getStatus(), VoucherViewSupport.STATUS_PUBLISHED)) {
            return Result.fail("已上架优惠券请先下架后再编辑");
        }
        Voucher voucher = buildVoucher(dto);
        voucher.setId(id);
        voucher.setStatus(existing.getStatus());
        validateVoucher(voucher);
        updateById(voucher);
        if (voucher.getType() != null && voucher.getType() == 1) {
            saveOrUpdateSeckillVoucher(voucher);
        } else {
            seckillVoucherService.removeById(id);
            stringRedisTemplate.delete(SECKILL_STOCK_KEY + id);
        }
        return Result.ok(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result publishVoucher(Long id) {
        Voucher voucher = getById(id);
        if (voucher == null) {
            return Result.fail("优惠券不存在");
        }
        fillVoucherRuntimeField(voucher);
        if (VoucherViewSupport.isExpired(voucher, LocalDateTime.now())) {
            voucher.setStatus(VoucherViewSupport.STATUS_EXPIRED);
            updateById(voucher);
            return Result.fail("优惠券已过期，无法上架");
        }
        validateVoucher(voucher);
        voucher.setStatus(VoucherViewSupport.STATUS_PUBLISHED);
        updateById(voucher);
        if (voucher.getType() != null && voucher.getType() == 1) {
            stringRedisTemplate.opsForValue().set(SECKILL_STOCK_KEY + voucher.getId(), String.valueOf(voucher.getStock()));
        }
        return Result.ok();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result unpublishVoucher(Long id) {
        Voucher voucher = getById(id);
        if (voucher == null) {
            return Result.fail("优惠券不存在");
        }
        fillVoucherRuntimeField(voucher);
        if (VoucherViewSupport.isExpired(voucher, LocalDateTime.now())) {
            voucher.setStatus(VoucherViewSupport.STATUS_EXPIRED);
        } else {
            voucher.setStatus(VoucherViewSupport.STATUS_UNPUBLISHED);
        }
        updateById(voucher);
        return Result.ok();
    }

    private Voucher buildVoucher(MerchantVoucherSaveDTO dto) {
        Voucher voucher = BeanUtil.copyProperties(dto, Voucher.class);
        voucher.setType(dto.getType() == null ? 0 : dto.getType());
        return voucher;
    }

    private void validateVoucher(Voucher voucher) {
        if (voucher.getShopId() == null || shopService.getById(voucher.getShopId()) == null) {
            throw new IllegalArgumentException("店铺不存在");
        }
        if (voucher.getPayValue() == null || voucher.getActualValue() == null
                || voucher.getPayValue() <= 0 || voucher.getActualValue() <= 0) {
            throw new IllegalArgumentException("优惠金额不合法");
        }
        if (voucher.getActualValue() < voucher.getPayValue()) {
            throw new IllegalArgumentException("抵扣金额不能小于支付金额");
        }
        if (voucher.getStock() == null || voucher.getStock() <= 0) {
            throw new IllegalArgumentException("库存必须大于0");
        }
        if (voucher.getType() != null && voucher.getType() == 1) {
            if (voucher.getBeginTime() == null || voucher.getEndTime() == null) {
                throw new IllegalArgumentException("秒杀券必须设置开始和结束时间");
            }
            if (!voucher.getEndTime().isAfter(voucher.getBeginTime())) {
                throw new IllegalArgumentException("结束时间必须晚于开始时间");
            }
        }
    }

    private void saveOrUpdateSeckillVoucher(Voucher voucher) {
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());
        seckillVoucherService.saveOrUpdate(seckillVoucher);
        stringRedisTemplate.opsForValue().set(SECKILL_STOCK_KEY + voucher.getId(), String.valueOf(voucher.getStock()));
    }

    private void fillVoucherRuntimeFields(List<Voucher> vouchers) {
        if (vouchers == null) {
            return;
        }
        vouchers.forEach(this::fillVoucherRuntimeField);
    }

    private void fillVoucherShopInfo(List<Voucher> vouchers) {
        if (vouchers == null || vouchers.isEmpty()) {
            return;
        }
        Set<Long> shopIds = vouchers.stream()
                .map(Voucher::getShopId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (shopIds.isEmpty()) {
            return;
        }
        Map<Long, Shop> shopMap = shopService.listByIds(shopIds).stream()
                .filter(Objects::nonNull)
                .filter(shop -> shop.getId() != null)
                .collect(Collectors.toMap(Shop::getId, Function.identity(), (left, right) -> left));
        vouchers.forEach(voucher -> {
            Shop shop = shopMap.get(voucher.getShopId());
            if (shop != null) {
                voucher.setShopName(shop.getName());
                voucher.setShopImages(VoucherOrderViewSupport.firstImage(shop.getImages()));
            }
        });
    }

    private void fillVoucherRuntimeField(Voucher voucher) {
        if (voucher == null || voucher.getType() == null || voucher.getType() != 1) {
            return;
        }
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucher.getId());
        if (seckillVoucher == null) {
            return;
        }
        voucher.setStock(seckillVoucher.getStock());
        voucher.setBeginTime(seckillVoucher.getBeginTime());
        voucher.setEndTime(seckillVoucher.getEndTime());
    }

    private void expireIfNeeded(List<Voucher> vouchers) {
        if (vouchers == null || vouchers.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<Voucher> expired = vouchers.stream()
                .filter(voucher -> VoucherViewSupport.isExpired(voucher, now)
                        && !Objects.equals(voucher.getStatus(), VoucherViewSupport.STATUS_EXPIRED))
                .collect(Collectors.toList());
        if (expired.isEmpty()) {
            return;
        }
        Set<Long> ids = expired.stream().map(Voucher::getId).collect(Collectors.toSet());
        update().set("status", VoucherViewSupport.STATUS_EXPIRED).in("id", ids).update();
        expired.forEach(voucher -> voucher.setStatus(VoucherViewSupport.STATUS_EXPIRED));
    }

    private Map<Long, List<VoucherOrder>> loadOrdersByVoucher(List<Voucher> vouchers) {
        if (vouchers == null || vouchers.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> voucherIds = vouchers.stream().map(Voucher::getId).collect(Collectors.toList());
        return voucherOrderService.lambdaQuery()
                .in(VoucherOrder::getVoucherId, voucherIds)
                .list()
                .stream()
                .collect(Collectors.groupingBy(VoucherOrder::getVoucherId));
    }
}
