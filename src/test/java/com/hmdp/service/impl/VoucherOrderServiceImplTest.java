package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.UpdateChainWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IShopService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoucherOrderServiceImplTest {

    @Mock
    private ISeckillVoucherService seckillVoucherService;
    @Mock
    private IVoucherService voucherService;
    @Mock
    private IShopService shopService;
    @Mock
    private RedisIdWorker redisIdWorker;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private VoucherOrderServiceImpl service;

    @BeforeEach
    void setUp() {
        service = org.mockito.Mockito.spy(new VoucherOrderServiceImpl());
        ReflectionTestUtils.setField(service, "seckillVoucherService", seckillVoucherService);
        ReflectionTestUtils.setField(service, "voucherService", voucherService);
        ReflectionTestUtils.setField(service, "shopService", shopService);
        ReflectionTestUtils.setField(service, "redisIdWorker", redisIdWorker);
        ReflectionTestUtils.setField(service, "redissonClient", redissonClient);
        ReflectionTestUtils.setField(service, "stringRedisTemplate", stringRedisTemplate);
        ReflectionTestUtils.setField(service, "voucherOrderService", mock(IVoucherOrderService.class));
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    @Test
    void receiveVoucherShouldCreatePendingOrderWhenVoucherAvailable() {
        UserHolder.saveUser(user(7L));

        Voucher voucher = new Voucher();
        voucher.setId(5L);
        voucher.setStatus(1);
        voucher.setStock(3);
        when(voucherService.getById(5L)).thenReturn(voucher);

        LambdaQueryChainWrapper<VoucherOrder> query = mock(LambdaQueryChainWrapper.class, Answers.RETURNS_SELF);
        doReturn(query).when(service).lambdaQuery();
        when(query.count()).thenReturn(0L);

        @SuppressWarnings("unchecked")
        UpdateChainWrapper<Voucher> updateChain = mock(UpdateChainWrapper.class, Answers.RETURNS_SELF);
        when(voucherService.update()).thenReturn(updateChain);
        when(updateChain.update()).thenReturn(true);
        when(redisIdWorker.nextId("order")).thenReturn(1001L);
        doReturn(true).when(service).save(any(VoucherOrder.class));

        Result result = service.receiveVoucher(5L);

        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(1001L);

        ArgumentCaptor<VoucherOrder> captor = ArgumentCaptor.forClass(VoucherOrder.class);
        verify(service).save(captor.capture());
        VoucherOrder savedOrder = captor.getValue();
        assertThat(savedOrder.getVoucherId()).isEqualTo(5L);
        assertThat(savedOrder.getUserId()).isEqualTo(7L);
        assertThat(savedOrder.getStatus()).isEqualTo(VoucherOrderFlowSupport.STATUS_PENDING_PAYMENT);
        assertThat(savedOrder.getPayType()).isEqualTo(1);
    }

    @Test
    void refundOrderShouldRestoreStockWhenTransitionAllowed() {
        UserHolder.saveUser(user(9L));

        VoucherOrder order = new VoucherOrder();
        order.setId(1L);
        order.setUserId(9L);
        order.setVoucherId(11L);
        order.setStatus(VoucherOrderFlowSupport.STATUS_PAID);
        Voucher voucher = new Voucher();
        voucher.setId(11L);
        voucher.setType(1);
        doReturn(order).when(service).getById(1L);
        doReturn(true).when(service).updateById(any(VoucherOrder.class));
        when(voucherService.getById(11L)).thenReturn(voucher);
        @SuppressWarnings("unchecked")
        UpdateChainWrapper<Voucher> updateChain = mock(UpdateChainWrapper.class, Answers.RETURNS_SELF);
        when(voucherService.update()).thenReturn(updateChain);
        when(updateChain.update()).thenReturn(true);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(eq("seckill:stock:11"))).thenReturn(1L);

        Result result = service.refundOrder(1L);

        assertThat(result.getSuccess()).isTrue();
        assertThat(order.getStatus()).isEqualTo(VoucherOrderFlowSupport.STATUS_REFUNDED);
        assertThat(order.getRefundTime()).isNotNull();
        verify(seckillVoucherService).update(any(LambdaUpdateWrapper.class));
        verify(valueOperations).increment("seckill:stock:11");
    }

    private UserDTO user(Long id) {
        UserDTO user = new UserDTO();
        user.setId(id);
        return user;
    }
}
