package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.service.IChatService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/chat")
public class ChatController {

    @Resource
    private IChatService chatService;

    @PostMapping("/send")
    public Result sendMessage(@RequestParam("receiverId") Long receiverId,
                              @RequestParam("content") String content) {
        return chatService.sendMessage(receiverId, content);
    }

    @GetMapping("/conversations")
    public Result queryConversations() {
        return chatService.queryConversations();
    }

    @GetMapping("/messages/{userId}")
    public Result queryMessages(@PathVariable("userId") Long userId,
                                @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return chatService.queryMessages(userId, current);
    }

    @PostMapping("/read/{userId}")
    public Result markAsRead(@PathVariable("userId") Long userId) {
        return chatService.markAsRead(userId);
    }

    @GetMapping("/can-send/{userId}")
    public Result canSend(@PathVariable("userId") Long userId) {
        return chatService.canSend(userId);
    }
}
