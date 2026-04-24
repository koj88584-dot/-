package com.hmdp.service.impl;

import com.hmdp.entity.VoucherOrder;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class VoucherOrderFlowSupportTest {

    @Test
    void validateTransitionShouldRejectMissingOrder() {
        String error = VoucherOrderFlowSupport.validateTransition(
                null,
                1L,
                true,
                VoucherOrderFlowSupport.STATUS_PENDING_PAYMENT,
                "订单状态不正确，无法取消"
        );

        assertThat(error).isEqualTo("订单不存在");
    }

    @Test
    void validateTransitionShouldRejectWrongOwner() {
        VoucherOrder order = new VoucherOrder();
        order.setUserId(9L);
        order.setStatus(VoucherOrderFlowSupport.STATUS_PENDING_PAYMENT);

        String error = VoucherOrderFlowSupport.validateTransition(
                order,
                18L,
                true,
                VoucherOrderFlowSupport.STATUS_PENDING_PAYMENT,
                "订单状态不正确，无法取消"
        );

        assertThat(error).isEqualTo("无权操作此订单");
    }

    @Test
    void validateTransitionShouldRejectWrongStatus() {
        VoucherOrder order = new VoucherOrder();
        order.setUserId(9L);
        order.setStatus(VoucherOrderFlowSupport.STATUS_PAID);

        String error = VoucherOrderFlowSupport.validateTransition(
                order,
                9L,
                true,
                VoucherOrderFlowSupport.STATUS_PENDING_PAYMENT,
                "订单状态不正确，无法支付"
        );

        assertThat(error).isEqualTo("订单状态不正确，无法支付");
    }

    @Test
    void markPaidAndRefundedShouldStampStatusAndTime() {
        VoucherOrder order = new VoucherOrder();
        LocalDateTime now = LocalDateTime.now();

        VoucherOrderFlowSupport.markPaid(order, now);

        assertThat(order.getStatus()).isEqualTo(VoucherOrderFlowSupport.STATUS_PAID);
        assertThat(order.getPayTime()).isEqualTo(now);

        VoucherOrderFlowSupport.markRefunded(order, now.plusMinutes(5));

        assertThat(order.getStatus()).isEqualTo(VoucherOrderFlowSupport.STATUS_REFUNDED);
        assertThat(order.getRefundTime()).isEqualTo(now.plusMinutes(5));
    }
}
