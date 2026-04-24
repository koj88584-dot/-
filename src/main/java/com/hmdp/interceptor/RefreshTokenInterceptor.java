package com.hmdp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 刷新 token 的拦截器。
 */
public class RefreshTokenInterceptor implements HandlerInterceptor {
    private final StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = request.getHeader("authorization");
        if (StringUtils.isEmpty(token)) {
            return true;
        }

        try {
            String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
            Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(tokenKey);
            if (userMap.isEmpty()) {
                return true;
            }

            UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
            if (userDTO.getId() == null) {
                stringRedisTemplate.delete(tokenKey);
                return true;
            }

            String userIndexKey = RedisConstants.LOGIN_USER_INDEX_KEY + userDTO.getId();
            String activeToken = stringRedisTemplate.opsForValue().get(userIndexKey);
            if (StringUtils.isNotEmpty(activeToken) && !token.equals(activeToken)) {
                stringRedisTemplate.delete(tokenKey);
                return true;
            }

            if (StringUtils.isEmpty(activeToken)) {
                stringRedisTemplate.opsForValue().set(userIndexKey, token, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
            } else {
                stringRedisTemplate.expire(userIndexKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
            }
            stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
            UserHolder.saveUser(userDTO);
        } catch (Exception ignored) {
            // Redis unavailable — skip token refresh, allow request to proceed
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserHolder.removeUser();
    }
}
