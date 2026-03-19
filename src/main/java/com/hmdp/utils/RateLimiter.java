package com.hmdp.utils;

import java.lang.annotation.*;

/**
 * API限流注解
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimiter {
    
    /**
     * 限流key前缀
     */
    String key() default "";
    
    /**
     * 时间窗口（秒）
     */
    int time() default 60;
    
    /**
     * 限制次数
     */
    int count() default 100;
    
    /**
     * 限流类型：IP-按IP限流 USER-按用户限流 ALL-全局限流
     */
    LimitType limitType() default LimitType.IP;
    
    /**
     * 限流提示信息
     */
    String message() default "请求过于频繁，请稍后再试";
}
