package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.ChatMessage;

public interface IChatService extends IService<ChatMessage> {

    Result sendMessage(Long receiverId, String content);

    Result queryConversations();

    Result queryMessages(Long userId, Integer current);

    Result markAsRead(Long userId);

    Result canSend(Long userId);
}
