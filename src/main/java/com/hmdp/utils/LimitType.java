package com.hmdp.utils;

/**
 * 限流类型枚举
 */
public enum LimitType {
    /**
     * 按IP限流
     */
    IP,
    
    /**
     * 按用户限流
     */
    USER,
    
    /**
     * 全局限流
     */
    ALL
}
