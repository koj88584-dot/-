package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.dto.VoucherOrderDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Shop;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IShopService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    private static final String ORDER_STREAM_KEY = "stream.orders";
    private static final String ORDER_GROUP = "g1";
    private static final String ORDER_CONSUMER = "c1";

    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private IVoucherService voucherService;
    @Resource
    private IShopService shopService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    @Lazy
    private IVoucherOrderService voucherOrderService;

    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(this::consumeOrderStream);
    }

    private void consumeOrderStream() {
        while (true) {
            try {
                List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream().read(
                        Consumer.from(ORDER_GROUP, ORDER_CONSUMER),
                        StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                        StreamOffset.create(ORDER_STREAM_KEY, ReadOffset.lastConsumed())
                );
                if (records == null || records.isEmpty()) {
                    continue;
                }
                handleOrderRecord(records.get(0));
            } catch (Exception e) {
                if (isRedisConnectionClosed(e)) {
                    log.error("Redis connection closed, stop voucher order consumer");
                    break;
                }
                log.error("Process voucher order stream failed", e);
                handlePendingList();
            }
        }
    }

    private void handlePendingList() {
        while (true) {
            try {
                List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream().read(
                        Consumer.from(ORDER_GROUP, ORDER_CONSUMER),
                        StreamReadOptions.empty().count(1),
                        StreamOffset.create(ORDER_STREAM_KEY, ReadOffset.from("0"))
                );
                if (records == null || records.isEmpty()) {
                    break;
                }
                handleOrderRecord(records.get(0));
            } catch (Exception e) {
                if (isRedisConnectionClosed(e)) {
                    log.error("Redis connection closed, stop pending voucher order handler");
                    break;
                }
                log.error("Process pending voucher order failed", e);
                try {
                    Thread.sleep(20);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(interruptedException);
                }
            }
        }
    }

    private void handleOrderRecord(MapRecord<String, Object, Object> record) {
        VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(record.getValue(), new VoucherOrder(), true);
        handleVoucherOrder(voucherOrder);
        stringRedisTemplate.opsForStream().acknowledge(ORDER_STREAM_KEY, ORDER_GROUP, record.getId());
    }

    private boolean isRedisConnectionClosed(Exception e) {
        return e.getMessage() != null
                && (e.getMessage().contains("was destroyed") || e.getMessage().contains("Connection"));
    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean locked = lock.tryLock();
        if (!locked) {
            throw new RuntimeException("不允许重复下单");
        }
        try {
            voucherOrderService.createVoucherOrder(voucherOrder);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        UserDTO user = UserHolder.getUser();
        Long orderId = redisIdWorker.nextId("order");
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                user.getId().toString(),
                orderId.toString()
        );
        int code = result == null ? -1 : result.intValue();
        if (code != 0) {
            return Result.fail(code == 1 ? "库存不足" : "禁止重复下单");
        }
        return Result.ok(orderId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        boolean success = seckillVoucherService.update(
                new LambdaUpdateWrapper<SeckillVoucher>()
                        .eq(SeckillVoucher::getVoucherId, voucherOrder.getVoucherId())
                        .gt(SeckillVoucher::getStock, 0)
                        .setSql("stock=stock-1")
        );
        if (!success) {
            throw new RuntimeException("库存不足");
        }
        voucherService.update()
                .setSql("stock = stock - 1")
                .eq("id", voucherOrder.getVoucherId())
                .gt("stock", 0)
                .update();
        save(voucherOrder);
    }

    @Override
    public Result queryMyOrders(Integer status, Integer current) {
        Long userId = UserHolder.getUser().getId();
        Page<VoucherOrder> page = lambdaQuery()
                .eq(VoucherOrder::getUserId, userId)
                .eq(status != null, VoucherOrder::getStatus, status)
                .orderByDesc(VoucherOrder::getCreateTime)
                .page(new Page<>(current, 10));
        return Result.ok(toOrderDTOs(page.getRecords()));
    }

    @Override
    public Result queryOrderById(Long id) {
        VoucherOrder order = getById(id);
        String error = VoucherOrderFlowSupport.validateExists(order);
        if (error != null) {
            return Result.fail(error);
        }
        List<VoucherOrderDTO> orders = toOrderDTOs(Collections.singletonList(order));
        return Result.ok(orders.isEmpty() ? null : orders.get(0));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result verifyOrder(Long id) {
        VoucherOrder order = getById(id);
        String error = VoucherOrderFlowSupport.validateTransition(
                order,
                null,
                false,
                VoucherOrderFlowSupport.STATUS_PAID,
                "订单状态不正确，无法核销"
        );
        if (error != null) {
            return Result.fail(error);
        }
        VoucherOrderFlowSupport.markVerified(order, LocalDateTime.now());
        updateById(order);
        return Result.ok();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result verifyOrderByCode(Long shopId, String verifyCode) {
        if (shopId == null) {
            return Result.fail("店铺不能为空");
        }
        if (verifyCode == null || verifyCode.trim().isEmpty()) {
            return Result.fail("券码不能为空");
        }
        VoucherOrder order = lambdaQuery()
                .eq(VoucherOrder::getVerifyCode, verifyCode.trim().toUpperCase())
                .one();
        if (order == null) {
            return Result.fail("券码不存在");
        }
        Voucher voucher = voucherService.getById(order.getVoucherId());
        if (voucher == null || !shopId.equals(voucher.getShopId())) {
            return Result.fail("券码与当前店铺不匹配");
        }
        if (order.getExpireTime() != null && order.getExpireTime().isBefore(LocalDateTime.now())) {
            return Result.fail("优惠券已过期");
        }
        String error = VoucherOrderFlowSupport.validateTransition(
                order,
                null,
                false,
                VoucherOrderFlowSupport.STATUS_PAID,
                "订单状态不正确，无法核销"
        );
        if (error != null) {
            return Result.fail(error);
        }
        VoucherOrderFlowSupport.markVerified(order, LocalDateTime.now());
        updateById(order);
        List<VoucherOrderDTO> orders = toOrderDTOs(Collections.singletonList(order));
        return Result.ok(orders.isEmpty() ? null : orders.get(0));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result cancelOrder(Long id) {
        VoucherOrder order = getById(id);
        Long userId = UserHolder.getUser().getId();
        String error = VoucherOrderFlowSupport.validateTransition(
                order,
                userId,
                true,
                VoucherOrderFlowSupport.STATUS_PENDING_PAYMENT,
                "订单状态不正确，无法取消"
        );
        if (error != null) {
            return Result.fail(error);
        }
        VoucherOrderFlowSupport.markCancelled(order, LocalDateTime.now());
        updateById(order);
        restoreVoucherStock(order.getVoucherId());
        return Result.ok();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result payOrder(Long id) {
        VoucherOrder order = getById(id);
        Long userId = UserHolder.getUser().getId();
        String error = VoucherOrderFlowSupport.validateTransition(
                order,
                userId,
                true,
                VoucherOrderFlowSupport.STATUS_PENDING_PAYMENT,
                "订单状态不正确，无法支付"
        );
        if (error != null) {
            return Result.fail(error);
        }
        Voucher voucher = voucherService.getById(order.getVoucherId());
        if (voucher == null) {
            return Result.fail("优惠券不存在");
        }
        fillVoucherRuntime(voucher);
        LocalDateTime now = LocalDateTime.now();
        VoucherOrderFlowSupport.markPaid(order, now);
        if (order.getVerifyCode() == null || order.getVerifyCode().trim().isEmpty()) {
            order.setVerifyCode(buildVerifyCode(order.getId()));
        }
        order.setEffectiveTime(resolveEffectiveTime(voucher, now));
        order.setExpireTime(resolveExpireTime(voucher));
        updateById(order);
        List<VoucherOrderDTO> orders = toOrderDTOs(Collections.singletonList(order));
        return Result.ok(orders.isEmpty() ? null : orders.get(0));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result refundOrder(Long id) {
        VoucherOrder order = getById(id);
        Long userId = UserHolder.getUser().getId();
        String error = VoucherOrderFlowSupport.validateTransition(
                order,
                userId,
                true,
                VoucherOrderFlowSupport.STATUS_PAID,
                "订单状态不正确，无法申请退款"
        );
        if (error != null) {
            return Result.fail(error);
        }
        VoucherOrderFlowSupport.markRefunded(order, LocalDateTime.now());
        updateById(order);
        restoreVoucherStock(order.getVoucherId());
        return Result.ok();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result receiveVoucher(Long voucherId) {
        Voucher voucher = voucherService.getById(voucherId);
        if (voucher == null) {
            return Result.fail("优惠券不存在");
        }
        if (voucher.getStatus() != 1) {
            return Result.fail("优惠券已下架或已过期");
        }
        if (voucher.getStock() == null || voucher.getStock() <= 0) {
            return Result.fail("优惠券已被领完");
        }

        Long userId = UserHolder.getUser().getId();
        Long count = lambdaQuery()
                .eq(VoucherOrder::getVoucherId, voucherId)
                .eq(VoucherOrder::getUserId, userId)
                .count();
        if (count > 0) {
            return Result.fail("您已领取过该优惠券");
        }

        boolean success = voucherService.update()
                .setSql("stock = stock - 1")
                .eq("id", voucherId)
                .gt("stock", 0)
                .update();
        if (!success) {
            return Result.fail("优惠券已被领完");
        }

        VoucherOrder voucherOrder = new VoucherOrder();
        Long orderId = redisIdWorker.nextId("order");
        LocalDateTime now = LocalDateTime.now();
        voucherOrder.setId(orderId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(userId);
        voucherOrder.setStatus(VoucherOrderFlowSupport.STATUS_PENDING_PAYMENT);
        voucherOrder.setPayType(1);
        voucherOrder.setCreateTime(now);
        voucherOrder.setUpdateTime(now);
        save(voucherOrder);
        return Result.ok(orderId);
    }

    @Override
    public Result queryMerchantOrders(Long shopId, Integer status, Integer current) {
        if (shopId == null) {
            return Result.fail("店铺不能为空");
        }
        List<Voucher> vouchers = voucherService.lambdaQuery()
                .eq(Voucher::getShopId, shopId)
                .list();
        if (vouchers.isEmpty()) {
            return Result.ok(Collections.emptyList(), 0L);
        }
        List<Long> voucherIds = vouchers.stream().map(Voucher::getId).collect(Collectors.toList());
        Page<VoucherOrder> page = lambdaQuery()
                .in(VoucherOrder::getVoucherId, voucherIds)
                .eq(status != null, VoucherOrder::getStatus, status)
                .orderByDesc(VoucherOrder::getCreateTime)
                .page(new Page<>(current == null || current < 1 ? 1 : current, 10));
        return Result.ok(toOrderDTOs(page.getRecords()), page.getTotal());
    }

    private List<VoucherOrderDTO> toOrderDTOs(List<VoucherOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> voucherIds = orders.stream()
                .map(VoucherOrder::getVoucherId)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, Voucher> voucherMap = voucherService.listByIds(voucherIds).stream()
                .collect(Collectors.toMap(Voucher::getId, Function.identity(), (left, right) -> left));

        List<Long> shopIds = voucherMap.values().stream()
                .map(Voucher::getShopId)
                .filter(shopId -> shopId != null && shopId > 0)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, Shop> shopMap = shopIds.isEmpty()
                ? Collections.emptyMap()
                : shopService.listByIds(shopIds).stream()
                .collect(Collectors.toMap(Shop::getId, Function.identity(), (left, right) -> left));

        return VoucherOrderViewSupport.toOrderDTOs(orders, voucherMap, shopMap);
    }

    private void restoreVoucherStock(Long voucherId) {
        Voucher voucher = voucherService.getById(voucherId);
        if (voucher == null) {
            return;
        }
        voucherService.update()
                .setSql("stock = stock + 1")
                .eq("id", voucherId)
                .update();
        if (voucher.getType() != null && voucher.getType() == 1) {
            seckillVoucherService.update(
                    new LambdaUpdateWrapper<SeckillVoucher>()
                            .eq(SeckillVoucher::getVoucherId, voucherId)
                            .setSql("stock=stock+1")
            );
            stringRedisTemplate.opsForValue().increment("seckill:stock:" + voucherId);
        }
    }

    private void fillVoucherRuntime(Voucher voucher) {
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

    private String buildVerifyCode(Long orderId) {
        return "VC" + Long.toString(orderId, 36).toUpperCase();
    }

    private LocalDateTime resolveEffectiveTime(Voucher voucher, LocalDateTime payTime) {
        if (voucher == null || voucher.getBeginTime() == null || voucher.getBeginTime().isBefore(payTime)) {
            return payTime;
        }
        return voucher.getBeginTime();
    }

    private LocalDateTime resolveExpireTime(Voucher voucher) {
        return voucher == null ? null : voucher.getEndTime();
    }
}
