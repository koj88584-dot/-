package com.hmdp.interceptor;

import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.LimitType;
import com.hmdp.utils.RateLimiter;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Collections;

/**
 * API限流切面
 */
@Slf4j
@Aspect
@Component
public class RateLimiterAspect {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final DefaultRedisScript<Long> LIMIT_SCRIPT;

    static {
        LIMIT_SCRIPT = new DefaultRedisScript<>();
        LIMIT_SCRIPT.setLocation(new ClassPathResource("ratelimit.lua"));
        LIMIT_SCRIPT.setResultType(Long.class);
    }

    @Around("@annotation(rateLimiter)")
    public Object around(ProceedingJoinPoint point, RateLimiter rateLimiter) throws Throwable {
        String key = buildKey(point, rateLimiter);
        int time = rateLimiter.time();
        int count = rateLimiter.count();

        // 执行Lua脚本
        Long currentCount = stringRedisTemplate.execute(
                LIMIT_SCRIPT,
                Collections.singletonList(key),
                String.valueOf(count),
                String.valueOf(time)
        );

        if (currentCount != null && currentCount == 0) {
            log.warn("限流触发: key={}, time={}, count={}", key, time, count);
            throw new RuntimeException(rateLimiter.message());
        }

        return point.proceed();
    }

    /**
     * 构建限流key
     */
    private String buildKey(ProceedingJoinPoint point, RateLimiter rateLimiter) {
        StringBuilder sb = new StringBuilder("rate_limit:");
        
        // 添加自定义key前缀
        if (StrUtil.isNotBlank(rateLimiter.key())) {
            sb.append(rateLimiter.key()).append(":");
        }
        
        // 获取方法签名
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        sb.append(method.getDeclaringClass().getName()).append(".");
        sb.append(method.getName()).append(":");
        
        // 根据限流类型添加后缀
        LimitType limitType = rateLimiter.limitType();
        switch (limitType) {
            case IP:
                sb.append(getClientIp());
                break;
            case USER:
                UserDTO user = UserHolder.getUser();
                if (user != null) {
                    sb.append(user.getId());
                } else {
                    sb.append(getClientIp());
                }
                break;
            case ALL:
                sb.append("all");
                break;
        }
        
        return sb.toString();
    }

    /**
     * 获取客户端IP
     */
    private String getClientIp() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "unknown";
        }
        
        HttpServletRequest request = attributes.getRequest();
        String ip = request.getHeader("X-Forwarded-For");
        
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        
        // 多个代理时取第一个IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        
        return ip;
    }
}
