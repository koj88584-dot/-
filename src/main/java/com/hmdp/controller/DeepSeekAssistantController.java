package com.hmdp.controller;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.DeepSeekChatRequestDTO;
import com.hmdp.dto.DeepSeekChatResponseDTO;
import com.hmdp.dto.Result;
import com.hmdp.service.IDeepSeekAssistantService;
import com.hmdp.utils.LimitType;
import com.hmdp.utils.RateLimiter;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/assistant")
public class DeepSeekAssistantController {

    @Resource
    private IDeepSeekAssistantService deepSeekAssistantService;
    private final ExecutorService assistantStreamExecutor = Executors.newCachedThreadPool();

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

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RateLimiter(
            key = "assistant-chat-stream",
            time = 60,
            count = 30,
            limitType = LimitType.IP,
            message = "提问太频繁了，请稍后再试"
    )
    public SseEmitter chatStream(@RequestBody DeepSeekChatRequestDTO requestDTO) {
        SseEmitter emitter = new SseEmitter(0L);
        assistantStreamExecutor.submit(() -> {
            try {
                Result result = deepSeekAssistantService.chat(requestDTO);
                DeepSeekChatResponseDTO payload = null;
                if (result != null && Boolean.TRUE.equals(result.getSuccess()) && result.getData() instanceof DeepSeekChatResponseDTO) {
                    payload = (DeepSeekChatResponseDTO) result.getData();
                }
                if (payload == null) {
                    payload = new DeepSeekChatResponseDTO();
                    payload.setConfigured(false);
                    payload.setReply(result != null ? result.getErrorMsg() : "智能助手暂时不可用");
                }

                Map<String, Object> meta = new HashMap<>();
                meta.put("configured", payload.getConfigured());
                meta.put("actions", payload.getActions());
                emitter.send(SseEmitter.event().name("meta").data(JSONUtil.toJsonStr(meta)));

                String reply = payload.getReply() == null ? "" : payload.getReply();
                int chunkSize = 24;
                for (int i = 0; i < reply.length(); i += chunkSize) {
                    int end = Math.min(i + chunkSize, reply.length());
                    String chunk = reply.substring(i, end);
                    emitter.send(SseEmitter.event().name("delta").data(chunk));
                    Thread.sleep(25);
                }

                emitter.send(SseEmitter.event().name("done").data(""));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }
}
