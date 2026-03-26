package com.hmdp.controller;

import com.hmdp.dto.DeepSeekChatRequestDTO;
import com.hmdp.dto.Result;
import com.hmdp.service.IDeepSeekAssistantService;
import com.hmdp.utils.LimitType;
import com.hmdp.utils.RateLimiter;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/assistant")
public class DeepSeekAssistantController {

    @Resource
    private IDeepSeekAssistantService deepSeekAssistantService;

    @PostMapping("/chat")
    @RateLimiter(
            key = "assistant-chat",
            time = 60,
            count = 30,
            limitType = LimitType.IP,
            message = "提问太频繁了，请稍后再试"
    )
    public Result chat(@RequestBody DeepSeekChatRequestDTO requestDTO) {
        return deepSeekAssistantService.chat(requestDTO);
    }
}
