package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.RegisterFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {

    /**
     * 发送验证码
     *
     * @param phone   手机号码
     * @param session 会话
     * @return {@link Result}
     */
    Result sendCode(String phone, HttpSession session);

    /**
     * 登录
     *
     * @param loginForm 登录表单
     * @param session   会话
     * @return {@link Result}
     */
    Result login(LoginFormDTO loginForm, HttpSession session);

    /**
     * 注册
     *
     * @param registerForm 注册表单
     * @param session      会话
     * @return {@link Result}
     */
    Result register(RegisterFormDTO registerForm, HttpSession session);

    /**
     * 签到
     *
     * @return {@link Result}
     */
    Result sign();

    /**
     * 统计连续签到
     *
     * @return {@link Result}
     */
    Result signCount();

    /**
     * 登出
     *
     * @param token token
     * @return {@link Result}
     */
    Result logout(String token);

    /**
     * 更新用户信息
     *
     * @param user 用户信息
     * @return {@link Result}
     */
    Result updateUserInfo(User user);

    /**
     * 上传用户头像
     *
     * @param url 头像URL
     * @return {@link Result}
     */
    Result uploadIcon(String url);
}
