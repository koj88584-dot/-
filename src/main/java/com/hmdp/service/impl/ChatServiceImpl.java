package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.ChatConversation;
import com.hmdp.entity.ChatMessage;
import com.hmdp.entity.Follow;
import com.hmdp.entity.PrivacySetting;
import com.hmdp.entity.User;
import com.hmdp.mapper.ChatConversationMapper;
import com.hmdp.mapper.ChatMessageMapper;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IChatService;
import com.hmdp.service.IPrivacySettingService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ChatServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessage> implements IChatService {

    @Resource
    private ChatConversationMapper chatConversationMapper;

    @Resource
    private FollowMapper followMapper;

    @Resource
    private IUserService userService;

    @Resource
    private IPrivacySettingService privacySettingService;

    @Override
    @Transactional
    public Result sendMessage(Long receiverId, String content) {
        UserDTO sender = UserHolder.getUser();
        if (sender == null) {
            return Result.fail("请先登录");
        }
        if (receiverId == null || sender.getId().equals(receiverId)) {
            return Result.fail("不能给自己发送消息");
        }
        if (StrUtil.isBlank(content)) {
            return Result.fail("消息内容不能为空");
        }
        String cleanContent = content.trim();
        if (cleanContent.length() > 2000) {
            return Result.fail("消息内容不能超过2000字");
        }
        User receiver = userService.getById(receiverId);
        if (receiver == null) {
            return Result.fail("接收用户不存在");
        }

        Map<String, Object> canSend = buildCanSendData(sender.getId(), receiverId);
        if (!Boolean.TRUE.equals(canSend.get("canSend"))) {
            return Result.fail(String.valueOf(canSend.get("reason")));
        }

        ChatConversation conversation = getOrCreateConversation(sender.getId(), receiverId);
        ChatMessage message = new ChatMessage();
        message.setConversationId(conversation.getId());
        message.setSenderId(sender.getId());
        message.setReceiverId(receiverId);
        message.setContent(cleanContent);
        message.setType(1);
        message.setIsRead(0);
        message.setCreateTime(LocalDateTime.now());
        save(message);

        conversation.setLastMessage(cleanContent.length() > 500 ? cleanContent.substring(0, 500) : cleanContent);
        conversation.setLastSenderId(sender.getId());
        conversation.setLastMessageTime(message.getCreateTime());
        if (receiverId.equals(conversation.getUserIdA())) {
            conversation.setUnreadCountA(nullToZero(conversation.getUnreadCountA()) + 1);
        } else {
            conversation.setUnreadCountB(nullToZero(conversation.getUnreadCountB()) + 1);
        }
        chatConversationMapper.updateById(conversation);

        return Result.ok(buildMessageItem(message, sender.getId()));
    }

    @Override
    public Result queryConversations() {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }

        List<ChatConversation> conversations = chatConversationMapper.selectList(new LambdaQueryWrapper<ChatConversation>()
                .eq(ChatConversation::getUserIdA, user.getId())
                .or()
                .eq(ChatConversation::getUserIdB, user.getId())
                .orderByDesc(ChatConversation::getLastMessageTime)
                .orderByDesc(ChatConversation::getCreateTime));
        if (conversations.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        List<Long> userIds = conversations.stream()
                .map(item -> user.getId().equals(item.getUserIdA()) ? item.getUserIdB() : item.getUserIdA())
                .collect(Collectors.toList());
        Map<Long, User> userMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, item -> item, (a, b) -> a));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (ChatConversation conversation : conversations) {
            Long otherUserId = user.getId().equals(conversation.getUserIdA())
                    ? conversation.getUserIdB()
                    : conversation.getUserIdA();
            User otherUser = userMap.get(otherUserId);
            Map<String, Object> row = new HashMap<>();
            row.put("id", conversation.getId());
            row.put("otherUserId", otherUserId);
            row.put("otherUserName", otherUser == null ? "用户" + otherUserId : otherUser.getNickName());
            row.put("otherUserIcon", otherUser == null ? "" : otherUser.getIcon());
            row.put("lastMessage", conversation.getLastMessage());
            row.put("lastSenderId", conversation.getLastSenderId());
            row.put("lastMessageTime", conversation.getLastMessageTime());
            row.put("unreadCount", user.getId().equals(conversation.getUserIdA())
                    ? nullToZero(conversation.getUnreadCountA())
                    : nullToZero(conversation.getUnreadCountB()));
            rows.add(row);
        }

        return Result.ok(rows);
    }

    @Override
    public Result queryMessages(Long userId, Integer current) {
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null) {
            return Result.fail("请先登录");
        }
        if (userId == null || currentUser.getId().equals(userId)) {
            return Result.fail("聊天对象不正确");
        }

        ChatConversation conversation = getConversation(currentUser.getId(), userId);
        if (conversation == null) {
            return Result.ok(Collections.emptyList());
        }

        Page<ChatMessage> page = lambdaQuery()
                .eq(ChatMessage::getConversationId, conversation.getId())
                .orderByDesc(ChatMessage::getCreateTime)
                .page(new Page<>(current == null ? 1 : current, SystemConstants.MAX_PAGE_SIZE));
        List<ChatMessage> records = page.getRecords();
        Collections.reverse(records);

        List<Map<String, Object>> rows = records.stream()
                .map(message -> buildMessageItem(message, currentUser.getId()))
                .collect(Collectors.toList());
        return Result.ok(rows, page.getTotal());
    }

    @Override
    @Transactional
    public Result markAsRead(Long userId) {
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null) {
            return Result.fail("请先登录");
        }
        ChatConversation conversation = getConversation(currentUser.getId(), userId);
        if (conversation == null) {
            return Result.ok();
        }

        lambdaUpdate()
                .eq(ChatMessage::getConversationId, conversation.getId())
                .eq(ChatMessage::getSenderId, userId)
                .eq(ChatMessage::getReceiverId, currentUser.getId())
                .set(ChatMessage::getIsRead, 1)
                .update();

        if (currentUser.getId().equals(conversation.getUserIdA())) {
            conversation.setUnreadCountA(0);
        } else {
            conversation.setUnreadCountB(0);
        }
        chatConversationMapper.updateById(conversation);
        return Result.ok();
    }

    @Override
    public Result canSend(Long userId) {
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null) {
            return Result.fail("请先登录");
        }
        return Result.ok(buildCanSendData(currentUser.getId(), userId));
    }

    private ChatConversation getOrCreateConversation(Long userId, Long otherUserId) {
        ChatConversation conversation = getConversation(userId, otherUserId);
        if (conversation != null) {
            return conversation;
        }

        Long userIdA = Math.min(userId, otherUserId);
        Long userIdB = Math.max(userId, otherUserId);
        conversation = new ChatConversation();
        conversation.setUserIdA(userIdA);
        conversation.setUserIdB(userIdB);
        conversation.setUnreadCountA(0);
        conversation.setUnreadCountB(0);
        conversation.setCreateTime(LocalDateTime.now());
        chatConversationMapper.insert(conversation);
        return conversation;
    }

    private ChatConversation getConversation(Long userId, Long otherUserId) {
        if (userId == null || otherUserId == null) {
            return null;
        }
        Long userIdA = Math.min(userId, otherUserId);
        Long userIdB = Math.max(userId, otherUserId);
        return chatConversationMapper.selectOne(new LambdaQueryWrapper<ChatConversation>()
                .eq(ChatConversation::getUserIdA, userIdA)
                .eq(ChatConversation::getUserIdB, userIdB));
    }

    private Map<String, Object> buildCanSendData(Long senderId, Long receiverId) {
        Map<String, Object> data = new HashMap<>();
        data.put("canSend", false);
        data.put("mutual", false);
        data.put("reason", "暂时无法发送消息");

        if (senderId == null || receiverId == null || senderId.equals(receiverId)) {
            data.put("reason", "聊天对象不正确");
            return data;
        }

        boolean mutual = isMutualFollow(senderId, receiverId);
        data.put("mutual", mutual);
        if (mutual) {
            data.put("canSend", true);
            data.put("reason", "");
            return data;
        }

        PrivacySetting receiverPrivacy = privacySettingService.getUserPrivacySetting(receiverId);
        if (receiverPrivacy != null && receiverPrivacy.getAllowMessage() != null && receiverPrivacy.getAllowMessage() == 0) {
            data.put("reason", "对方暂未开放陌生人私信");
            return data;
        }

        Long sentCount = lambdaQuery()
                .eq(ChatMessage::getSenderId, senderId)
                .eq(ChatMessage::getReceiverId, receiverId)
                .count();
        Long receivedCount = lambdaQuery()
                .eq(ChatMessage::getSenderId, receiverId)
                .eq(ChatMessage::getReceiverId, senderId)
                .count();

        if (sentCount >= 1 && receivedCount == 0) {
            data.put("reason", "对方还未回复，暂时无法继续发送");
            return data;
        }

        data.put("canSend", true);
        data.put("reason", "");
        return data;
    }

    private boolean isMutualFollow(Long userId, Long otherUserId) {
        Long forward = followMapper.selectCount(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getUserId, userId)
                .eq(Follow::getFollowUserId, otherUserId));
        if (forward <= 0) {
            return false;
        }
        Long backward = followMapper.selectCount(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getUserId, otherUserId)
                .eq(Follow::getFollowUserId, userId));
        return backward > 0;
    }

    private Map<String, Object> buildMessageItem(ChatMessage message, Long currentUserId) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", message.getId());
        item.put("conversationId", message.getConversationId());
        item.put("senderId", message.getSenderId());
        item.put("receiverId", message.getReceiverId());
        item.put("content", message.getContent());
        item.put("type", message.getType());
        item.put("isRead", message.getIsRead());
        item.put("createTime", message.getCreateTime());
        item.put("mine", currentUserId != null && currentUserId.equals(message.getSenderId()));
        return item;
    }

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }
}
