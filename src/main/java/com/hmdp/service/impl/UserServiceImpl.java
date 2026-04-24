package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.RegisterFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.PasswordEncoder;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_CODE_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_CODE_TTL;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_INDEX_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;
import static com.hmdp.utils.RedisConstants.USER_SIGN_KEY;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private MerchantAuthService merchantAuthService;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        String code = RandomUtil.randomNumbers(6);
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        log.debug("发送验证码成功，手机号：{}，验证码：{}", phone, code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }

        String password = loginForm.getPassword();
        String code = loginForm.getCode();
        User user = queryUserByPhone(phone);

        if (StrUtil.isBlank(password) && StrUtil.isBlank(code)) {
            return Result.fail("请输入验证码或密码");
        }

        if (StrUtil.isNotBlank(password) && StrUtil.isBlank(code)) {
            if (user == null) {
                return Result.fail("账号不存在，请先注册");
            }
            if (StrUtil.isBlank(user.getPassword())) {
                return Result.fail("该账号尚未设置密码，请使用验证码登录");
            }
            if (!PasswordEncoder.matches(user.getPassword(), password)) {
                return Result.fail("手机号或密码错误");
            }
            return Result.ok(createLoginToken(user));
        }

        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        if (!StrUtil.equals(cacheCode, code)) {
            return Result.fail("验证码错误");
        }

        if (user == null) {
            user = createUserWithPhone(phone);
        }

        String token = createLoginToken(user);
        stringRedisTemplate.delete(LOGIN_CODE_KEY + phone);
        return Result.ok(token);
    }

    @Override
    public Result register(RegisterFormDTO registerForm, HttpSession session) {
        String phone = registerForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        if (StrUtil.isBlank(registerForm.getCode())) {
            return Result.fail("请输入验证码");
        }
        if (StrUtil.isBlank(registerForm.getPassword()) || registerForm.getPassword().length() < 6) {
            return Result.fail("密码长度不能少于6位");
        }
        if (!StrUtil.equals(registerForm.getPassword(), registerForm.getConfirmPassword())) {
            return Result.fail("两次输入的密码不一致");
        }
        if (queryUserByPhone(phone) != null) {
            return Result.fail("该手机号已注册，请直接登录");
        }

        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        if (!StrUtil.equals(cacheCode, registerForm.getCode())) {
            return Result.fail("验证码错误");
        }

        User user = createUserWithPhone(phone);
        user.setPassword(PasswordEncoder.encode(registerForm.getPassword()));
        if (StrUtil.isNotBlank(registerForm.getNickName())) {
            user.setNickName(registerForm.getNickName());
        }
        updateById(user);
        stringRedisTemplate.delete(LOGIN_CODE_KEY + phone);
        return Result.ok(createLoginToken(user));
    }

    @Override
    public Result sign() {
        Long id = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String yyyyMM = now.format(DateTimeFormatter.ofPattern("yyyy:MM:"));
        String key = USER_SIGN_KEY + yyyyMM + id;
        int dayOfMonth = now.getDayOfMonth();
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        Long id = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String yyyyMM = now.format(DateTimeFormatter.ofPattern("yyyy:MM:"));
        String key = USER_SIGN_KEY + yyyyMM + id;
        int dayOfMonth = now.getDayOfMonth();

        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                        .valueAt(0)
        );
        if (result == null || result.isEmpty()) {
            return Result.ok(0);
        }

        Long num = result.get(0);
        if (num == null || num == 0) {
            return Result.ok(0);
        }

        String binaryString = Long.toBinaryString(num);
        int count = 0;
        for (int i = binaryString.length() - 1; i >= 0; i--) {
            if (binaryString.charAt(i) == '1') {
                count++;
            } else {
                break;
            }
        }
        return Result.ok(count);
    }

    private User queryUserByPhone(String phone) {
        return baseMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, phone));
    }

    private String createLoginToken(User user) {
        Long userId = user.getId();
        String userIndexKey = LOGIN_USER_INDEX_KEY + userId;
        String previousToken = stringRedisTemplate.opsForValue().get(userIndexKey);
        if (StrUtil.isNotBlank(previousToken)) {
            stringRedisTemplate.delete(LOGIN_USER_KEY + previousToken);
            stringRedisTemplate.delete(userIndexKey);
        }

        String token = UUID.randomUUID().toString(true);
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        merchantAuthService.fillUserFlags(userDTO);
        Map<String, Object> map = BeanUtil.beanToMap(
                userDTO,
                new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((name, value) -> value == null ? null : value.toString())
        );
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, map);
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
        stringRedisTemplate.opsForValue().set(userIndexKey, token, LOGIN_USER_TTL, TimeUnit.MINUTES);
        return token;
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        baseMapper.insert(user);
        return user;
    }

    @Override
    public Result logout(String token) {
        if (StrUtil.isNotBlank(token)) {
            String tokenKey = LOGIN_USER_KEY + token;
            Object userIdValue = stringRedisTemplate.opsForHash().get(tokenKey, "id");
            stringRedisTemplate.delete(tokenKey);

            Long userId = null;
            if (userIdValue != null && StrUtil.isNotBlank(userIdValue.toString())) {
                userId = Long.valueOf(userIdValue.toString());
            } else if (UserHolder.getUser() != null) {
                userId = UserHolder.getUser().getId();
            }

            if (userId != null) {
                String userIndexKey = LOGIN_USER_INDEX_KEY + userId;
                String currentToken = stringRedisTemplate.opsForValue().get(userIndexKey);
                if (StrUtil.equals(currentToken, token)) {
                    stringRedisTemplate.delete(userIndexKey);
                }
            }
        }
        UserHolder.removeUser();
        return Result.ok();
    }

    @Override
    public Result updateUserInfo(User user) {
        Long userId = UserHolder.getUser().getId();
        if (!userId.equals(user.getId())) {
            return Result.fail("无权修改他人信息");
        }

        User oldUser = getById(userId);
        if (oldUser == null) {
            return Result.fail("用户不存在");
        }

        if (StrUtil.isNotBlank(user.getNickName())) {
            oldUser.setNickName(user.getNickName());
        }
        if (StrUtil.isNotBlank(user.getIcon())) {
            oldUser.setIcon(user.getIcon());
        }

        updateById(oldUser);
        return Result.ok();
    }

    @Override
    public Result uploadIcon(String url) {
        Long userId = UserHolder.getUser().getId();
        User user = getById(userId);
        if (user == null) {
            return Result.fail("用户不存在");
        }

        user.setIcon(url);
        updateById(user);
        return Result.ok(url);
    }
}
