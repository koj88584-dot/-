package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Favorites;
import com.hmdp.entity.UserMessage;
import com.hmdp.mapper.UserMessageMapper;
import com.hmdp.service.IFavoritesService;
import com.hmdp.service.IUserMessageService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户消息服务实现
 */
@Service
public class UserMessageServiceImpl extends ServiceImpl<UserMessageMapper, UserMessage> implements IUserMessageService {

    @Resource
    private IFavoritesService favoritesService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final String USER_MESSAGE_COUNT_KEY = "message:count:";

    @Override
    public Result queryMessages(Integer current, Integer type) {
        Long userId = UserHolder.getUser().getId();

        Page<UserMessage> page = lambdaQuery()
                .eq(UserMessage::getUserId, userId)
                .eq(type != null, UserMessage::getType, type)
                .orderByDesc(UserMessage::getCreateTime)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));

        return Result.ok(page.getRecords());
    }

    @Override
    public Result getUnreadCount() {
        Long userId = UserHolder.getUser().getId();

        // 先从Redis获取
        String key = USER_MESSAGE_COUNT_KEY + userId;
        String countStr = stringRedisTemplate.opsForValue().get(key);

        if (countStr != null) {
            return Result.ok(Integer.parseInt(countStr));
        }

        // Redis中没有，从数据库查询
        long count = lambdaQuery()
                .eq(UserMessage::getUserId, userId)
                .eq(UserMessage::getIsRead, 0)
                .count();

        // 缓存到Redis，设置5分钟过期
        stringRedisTemplate.opsForValue().set(key, String.valueOf(count), 5, java.util.concurrent.TimeUnit.MINUTES);

        return Result.ok((int) count);
    }

    @Override
    public Result markAsRead(Long messageId) {
        Long userId = UserHolder.getUser().getId();

        if (messageId != null) {
            // 标记单条为已读
            lambdaUpdate()
                    .eq(UserMessage::getId, messageId)
                    .eq(UserMessage::getUserId, userId)
                    .set(UserMessage::getIsRead, 1)
                    .set(UserMessage::getReadTime, LocalDateTime.now())
                    .update();
        } else {
            // 标记所有为已读
            lambdaUpdate()
                    .eq(UserMessage::getUserId, userId)
                    .eq(UserMessage::getIsRead, 0)
                    .set(UserMessage::getIsRead, 1)
                    .set(UserMessage::getReadTime, LocalDateTime.now())
                    .update();
        }

        // 清除未读数缓存
        stringRedisTemplate.delete(USER_MESSAGE_COUNT_KEY + userId);

        return Result.ok();
    }

    @Override
    public Result deleteMessage(Long messageId) {
        Long userId = UserHolder.getUser().getId();

        boolean removed = lambdaUpdate()
                .eq(UserMessage::getId, messageId)
                .eq(UserMessage::getUserId, userId)
                .remove();

        return removed ? Result.ok() : Result.fail("删除失败");
    }

    @Override
    public Result clearAllMessages() {
        Long userId = UserHolder.getUser().getId();

        lambdaUpdate()
                .eq(UserMessage::getUserId, userId)
                .remove();

        // 清除未读数缓存
        stringRedisTemplate.delete(USER_MESSAGE_COUNT_KEY + userId);

        return Result.ok();
    }

    @Override
    public Result pushShopUpdateMessage(Long shopId, Integer type, String title, String content, String images) {
        // 查询所有收藏该店铺的用户
        List<Favorites> favorites = favoritesService.lambdaQuery()
                .eq(Favorites::getType, 1) // 店铺类型
                .eq(Favorites::getTargetId, shopId)
                .list();

        if (favorites.isEmpty()) {
            return Result.ok("没有用户收藏该店铺");
        }

        // 给每个收藏用户发送消息
        List<UserMessage> messages = favorites.stream().map(fav -> {
            UserMessage message = new UserMessage();
            message.setUserId(fav.getUserId());
            message.setShopId(shopId);
            message.setType(type);
            message.setTitle(title);
            message.setContent(content);
            message.setImages(images);
            message.setIsRead(0);
            message.setCreateTime(LocalDateTime.now());
            return message;
        }).collect(Collectors.toList());

        // 批量保存
        saveBatch(messages);

        // 增加未读数缓存
        for (Favorites fav : favorites) {
            String key = USER_MESSAGE_COUNT_KEY + fav.getUserId();
            stringRedisTemplate.opsForValue().increment(key);
            stringRedisTemplate.expire(key, 5, java.util.concurrent.TimeUnit.MINUTES);
        }

        return Result.ok("已推送给 " + messages.size() + " 位用户");
    }
}
