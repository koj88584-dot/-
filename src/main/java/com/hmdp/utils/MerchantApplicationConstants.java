package com.hmdp.utils;

public final class MerchantApplicationConstants {

    private MerchantApplicationConstants() {
    }

    public static final Integer STATUS_PENDING = 0;
    public static final Integer STATUS_APPROVED = 1;
    public static final Integer STATUS_REJECTED = 2;

    public static final String PROFILE_STATUS_NONE = "NONE";
    public static final String PROFILE_STATUS_PENDING = "PENDING";
    public static final String PROFILE_STATUS_APPROVED = "APPROVED";
    public static final String PROFILE_STATUS_REJECTED = "REJECTED";

    public static String toProfileStatus(Integer status) {
        if (status == null) {
            return PROFILE_STATUS_NONE;
        }
        if (STATUS_PENDING.equals(status)) {
            return PROFILE_STATUS_PENDING;
        }
        if (STATUS_APPROVED.equals(status)) {
            return PROFILE_STATUS_APPROVED;
        }
        if (STATUS_REJECTED.equals(status)) {
            return PROFILE_STATUS_REJECTED;
        }
        return PROFILE_STATUS_NONE;
    }

    public static String toStatusText(Integer status) {
        if (STATUS_PENDING.equals(status)) {
            return "待审核";
        }
        if (STATUS_APPROVED.equals(status)) {
            return "已通过";
        }
        if (STATUS_REJECTED.equals(status)) {
            return "已驳回";
        }
        return "未提交";
    }
}
