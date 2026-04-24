package com.hmdp.service.impl;

import com.hmdp.dto.MerchantVoucherDTO;
import com.hmdp.entity.Voucher;

import java.time.LocalDateTime;

final class VoucherViewSupport {

    static final int STATUS_DRAFT = 0;
    static final int STATUS_PUBLISHED = 1;
    static final int STATUS_UNPUBLISHED = 2;
    static final int STATUS_EXPIRED = 3;

    private VoucherViewSupport() {
    }

    static boolean isExpired(Voucher voucher, LocalDateTime now) {
        return voucher != null
                && voucher.getEndTime() != null
                && now != null
                && voucher.getEndTime().isBefore(now);
    }

    static String statusText(Integer status) {
        if (status == null) {
            return "未知状态";
        }
        switch (status) {
            case STATUS_DRAFT:
                return "草稿";
            case STATUS_PUBLISHED:
                return "已上架";
            case STATUS_UNPUBLISHED:
                return "已下架";
            case STATUS_EXPIRED:
                return "已结束";
            default:
                return "未知状态";
        }
    }

    static void fillStatusText(MerchantVoucherDTO dto) {
        if (dto != null) {
            dto.setStatusText(statusText(dto.getStatus()));
        }
    }

    static String orderStatusText(Integer status) {
        if (status == null) {
            return "未知状态";
        }
        switch (status) {
            case VoucherOrderFlowSupport.STATUS_PENDING_PAYMENT:
                return "待支付";
            case VoucherOrderFlowSupport.STATUS_PAID:
                return "已支付";
            case VoucherOrderFlowSupport.STATUS_VERIFIED:
                return "已核销";
            case VoucherOrderFlowSupport.STATUS_CANCELLED:
                return "已取消";
            case VoucherOrderFlowSupport.STATUS_REFUNDED:
                return "已退款";
            case 5:
                return "退款中";
            default:
                return "未知状态";
        }
    }
}
