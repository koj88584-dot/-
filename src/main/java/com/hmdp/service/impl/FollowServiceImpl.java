package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IPrivacySettingService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService userService;
    @Resource
    private IPrivacySettingService privacySettingService;

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        //获取登陆用户
        Long id = UserHolder.getUser().getId();
        //判断是关注还是取关
        if (isFollow) {
            //关注 新增数据
            Follow follow=new Follow();
            follow.setFollowUserId(followUserId);
            follow.setUserId(id);
            boolean isSuccess = save(follow);
            if (isSuccess){
                String key="follows:"+id;
                stringRedisTemplate.opsForSet().add(key,followUserId.toString());
            }
        }else {
            //取关 删除
            boolean isSuccess = remove(new LambdaQueryWrapper<Follow>()
                    .eq(Follow::getUserId, id)
                    .eq(Follow::getFollowUserId, followUserId)
            );
            if (isSuccess) {
                String key="follows:"+id;
                stringRedisTemplate.opsForSet().remove(key,followUserId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        //获取登陆用户
        Long id = UserHolder.getUser().getId();
        //查询是否关注
        Long count = lambdaQuery()
                .eq(Follow::getUserId, id)
                .eq(Follow::getFollowUserId, followUserId)
                .count();
        return Result.ok(count>0);
    }

    @Override
    public Result isMutualFollow(Long targetUserId) {
        Long userId = UserHolder.getUser().getId();
        if (userId == null || targetUserId == null || userId.equals(targetUserId)) {
            return Result.ok(false);
        }

        Long forwardCount = lambdaQuery()
                .eq(Follow::getUserId, userId)
                .eq(Follow::getFollowUserId, targetUserId)
                .count();
        if (forwardCount <= 0) {
            return Result.ok(false);
        }

        Long backwardCount = lambdaQuery()
                .eq(Follow::getUserId, targetUserId)
                .eq(Follow::getFollowUserId, userId)
                .count();
        return Result.ok(backwardCount > 0);
    }

    @Override
    public Result followCommons(Long id) {
        if (!privacySettingService.canViewFollowing(id)) {
            return Result.fail("对方已隐藏关注列表");
        }
        //获取登陆用户
        Long userId = UserHolder.getUser().getId();
        String key="follows:"+userId;
        //求交集
        String key2="follows:"+id;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key, key2);
        if (intersect==null||intersect.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        //解析出id
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        //查询用户
        List<User> users = userService.listByIds(ids);
        List<UserDTO> collect = users.stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(collect);
    }

    @Override
    public Result queryFollowList(Long userId, Integer current) {
        if (!privacySettingService.canViewFollowing(userId)) {
            return Result.fail("对方已隐藏关注列表");
        }
        // 查询当前用户的关注列表
        List<Follow> follows = lambdaQuery()
                .eq(Follow::getUserId, userId)
                .orderByDesc(Follow::getCreateTime)
                .page(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(current, 10))
                .getRecords();

        if (follows.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        // 获取被关注的用户ID列表
        List<Long> followUserIds = follows.stream()
                .map(Follow::getFollowUserId)
                .collect(Collectors.toList());

        // 查询用户信息
        List<User> users = userService.listByIds(followUserIds);

        // 转换为DTO
        List<UserDTO> userDTOs = users.stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(userDTOs);
    }

    @Override
    public Result queryFollowerList(Long userId, Integer current) {
        if (!privacySettingService.canViewFollowers(userId)) {
            return Result.fail("对方已隐藏粉丝列表");
        }
        // 查询当前用户的粉丝列表（谁关注了我）
        List<Follow> followers = lambdaQuery()
                .eq(Follow::getFollowUserId, userId)
                .orderByDesc(Follow::getCreateTime)
                .page(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(current, 10))
                .getRecords();

        if (followers.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        // 获取粉丝的用户ID列表
        List<Long> followerUserIds = followers.stream()
                .map(Follow::getUserId)
                .collect(Collectors.toList());

        // 查询用户信息
        List<User> users = userService.listByIds(followerUserIds);

        // 转换为DTO
        List<UserDTO> userDTOs = users.stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(userDTOs);
    }
}
