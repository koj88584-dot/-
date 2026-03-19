package com.hmdp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 登录拦截器
 *
 * @author CHEN
 * @date 2022/10/07
 */
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 放行OPTIONS请求（CORS预检请求）
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        
        String requestURI = request.getRequestURI();
        UserDTO user = UserHolder.getUser();
        
        System.out.println("\n========== LoginInterceptor ==========");
        System.out.println("请求路径: " + requestURI);
        System.out.println("ThreadLocal中的用户: " + (user != null ? user : "(null)"));
        
        //获取用户
        if (user == null) {
            //不存在用户 拦截
            System.out.println("结果: 用户未登录，返回401");
            System.out.println("========================================\n");
            response.setStatus(401);
            return false;
        }
        
        //存在用户放行
        System.out.println("结果: 用户已登录，放行");
        System.out.println("========================================\n");
        return true;
    }


}
