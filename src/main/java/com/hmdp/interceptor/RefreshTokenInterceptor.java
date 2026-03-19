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
 * 刷新令牌拦截器
 *
 * @author CHEN
 * @date 2022/10/07
 */
public class RefreshTokenInterceptor implements HandlerInterceptor {
    private final StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 放行OPTIONS请求（CORS预检请求）
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            System.out.println("\n========== RefreshTokenInterceptor ==========");
            System.out.println("CORS预检请求(OPTIONS)，直接放行");
            System.out.println("========================================\n");
            return true;
        }
        
        //从请求头中获取token
        String token = request.getHeader("authorization");
        String requestURI = request.getRequestURI();
        
        System.out.println("\n========== RefreshTokenInterceptor ==========");
        System.out.println("请求路径: " + requestURI);
        System.out.println("请求方法: " + request.getMethod());
        System.out.println("authorization header: " + (token != null ? token : "(null)"));
        
        if (StringUtils.isEmpty(token)) {
            //不存在token
            System.out.println("结果: token为空，放行（交给LoginInterceptor处理）");
            System.out.println("========================================\n");
            return true;
        }
        
        //从redis中获取用户
        String key = RedisConstants.LOGIN_USER_KEY + token;
        System.out.println("Redis key: " + key);
        
        Map<Object, Object> userMap =
                stringRedisTemplate.opsForHash()
                        .entries(key);
        
        System.out.println("Redis查询结果: " + (userMap.isEmpty() ? "(空，key不存在或已过期)" : "找到用户"));
        
        //用户不存在
        if (userMap.isEmpty()) {
            System.out.println("结果: Redis中无用户，放行（交给LoginInterceptor处理）");
            System.out.println("========================================\n");
            return true;
        }
        
        //hash转UserDTO存入ThreadLocal
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        UserHolder.saveUser(userDTO);
        System.out.println("用户已保存到ThreadLocal: " + userDTO);
        
        //token续命
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        System.out.println("结果: token续命成功，放行");
        System.out.println("========================================\n");
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
