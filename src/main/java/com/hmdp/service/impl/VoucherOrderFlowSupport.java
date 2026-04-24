package com.hmdp.service.impl;

import com.hmdp.entity.VoucherOrder;

import java.time.LocalDateTime;

final class VoucherOrderFlowSupport {

    static final int STATUS_PENDING_PAYMENT = 1;
    static final int STATUS_PAID = 2;
    static final int STATUS_VERIFIED = 3;
    static final int STATUS_CANCELLED = 4;
    static final int STATUS_REFUNDED = 6;

    private static final String ORDER_NOT_FOUND_MESSAGE = "订单不存在";
    private static final String ORDER_FORBIDDEN_MESSAGE = "无权操作此订单";

    private VoucherOrderFlowSupport() {
    }

    static String validateExists(VoucherOrder order) {
        return order == null ? ORDER_NOT_FOUND_MESSAGE : null;
    }

    static String validateTransition(VoucherOrder order, Long userId, boolean requireOwner,
                                     int expectedStatus, String invalidStatusMessage) {
        String existsError = validateExists(order);
        if (existsError != null) {
            return existsError;
        }
        if (requireOwner && (userId == null || !userId.equals(order.getUserId()))) {
            return ORDER_FORBIDDEN_MESSAGE;
        }
        if (order.getStatus() == null || order.getStatus() != expectedStatus) {
            return invalidStatusMessage;
        }
        return null;
    }

    static void markVerified(VoucherOrder order, LocalDateTime now) {
        order.setStatus(STATUS_VERIFIED);
        order.setUseTime(now);
        order.setUpdateTime(now);
    }

    static void markCancelled(VoucherOrder order, LocalDateTime now) {
        order.setStatus(STATUS_CANCELLED);
        order.setUpdateTime(now);
    }

    static void markPaid(VoucherOrder order, LocalDateTime now) {
        order.setStatus(STATUS_PAID);
        order.setPayTime(now);
        order.setUpdateTime(now);
    }

    static void markRefunded(VoucherOrder order, LocalDateTime now) {
        order.setStatus(STATUS_REFUNDED);
        order.setRefundTime(now);
        order.setUpdateTime(now);
    }
}
