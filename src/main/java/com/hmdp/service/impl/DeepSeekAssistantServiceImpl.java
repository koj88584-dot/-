package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.AssistantActionDTO;
import com.hmdp.dto.AssistantMessageDTO;
import com.hmdp.dto.DeepSeekChatRequestDTO;
import com.hmdp.dto.DeepSeekChatResponseDTO;
import com.hmdp.dto.Result;
import com.hmdp.service.IDeepSeekAssistantService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class DeepSeekAssistantServiceImpl implements IDeepSeekAssistantService {

    @Value("${deepseek.api-key:}")
    private String apiKey;

    @Value("${deepseek.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${deepseek.model:deepseek-chat}")
    private String model;

    @Override
    public Result chat(DeepSeekChatRequestDTO requestDTO) {
        if (requestDTO == null || StrUtil.isBlank(requestDTO.getMessage())) {
            return Result.fail("请输入你想咨询的问题");
        }

        String userMessage = requestDTO.getMessage().trim();
        List<AssistantActionDTO> actions = buildActions(userMessage);

        DeepSeekChatResponseDTO responseDTO = new DeepSeekChatResponseDTO();
        responseDTO.setActions(actions);

        if (StrUtil.isBlank(apiKey)) {
            responseDTO.setConfigured(false);
            responseDTO.setReply(buildFallbackReply(userMessage, actions, true));
            return Result.ok(responseDTO);
        }

        try {
            responseDTO.setReply(callDeepSeek(requestDTO));
            responseDTO.setConfigured(true);
            return Result.ok(responseDTO);
        } catch (Exception e) {
            responseDTO.setConfigured(false);
            responseDTO.setReply(buildFallbackReply(userMessage, actions, false));
            return Result.ok(responseDTO);
        }
    }

    private String callDeepSeek(DeepSeekChatRequestDTO requestDTO) {
        JSONArray messages = new JSONArray();
        messages.add(JSONUtil.createObj()
                .set("role", "system")
                .set("content", buildSystemPrompt()));

        if (requestDTO.getHistory() != null) {
            for (AssistantMessageDTO historyItem : requestDTO.getHistory()) {
                if (historyItem == null || StrUtil.isBlank(historyItem.getContent())) {
                    continue;
                }
                String role = StrUtil.blankToDefault(historyItem.getRole(), "user");
                if (!"user".equals(role) && !"assistant".equals(role)) {
                    continue;
                }
                messages.add(JSONUtil.createObj()
                        .set("role", role)
                        .set("content", historyItem.getContent().trim()));
            }
        }

        messages.add(JSONUtil.createObj()
                .set("role", "user")
                .set("content", buildUserPrompt(requestDTO)));

        JSONObject body = JSONUtil.createObj()
                .set("model", model)
                .set("messages", messages)
                .set("stream", false)
                .set("temperature", 0.6);

        String responseText = HttpRequest.post(normalizeBaseUrl() + "/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(body.toString())
                .timeout(20000)
                .execute()
                .body();

        JSONObject response = JSONUtil.parseObj(responseText);
        if (response.containsKey("error")) {
            throw new IllegalStateException(response.getByPath("error.message", String.class));
        }

        JSONArray choices = response.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("DeepSeek 未返回有效结果");
        }

        JSONObject firstChoice = choices.getJSONObject(0);
        JSONObject message = firstChoice.getJSONObject("message");
        if (message == null || StrUtil.isBlank(message.getStr("content"))) {
            throw new IllegalStateException("DeepSeek 返回内容为空");
        }
        return message.getStr("content").trim();
    }

    private String buildSystemPrompt() {
        return "你是 HMDP 本地生活应用里的智能助手。"
                + "你需要用简洁自然的中文回答用户问题，并优先给出可执行建议。"
                + "这个应用支持搜索店铺和笔记、查看附近商铺、领取优惠券、下单支付、发布探店笔记、查看消息、收藏和浏览历史。"
                + "如果问题涉及应用内操作，请告诉用户应该先进入哪个页面，再完成哪一步。"
                + "不要编造不存在的功能，不要输出 Markdown 标题，不要写得太长。";
    }

    private String buildUserPrompt(DeepSeekChatRequestDTO requestDTO) {
        StringBuilder builder = new StringBuilder();
        if (StrUtil.isNotBlank(requestDTO.getScene())) {
            builder.append("当前页面场景：").append(requestDTO.getScene().trim()).append('\n');
        }
        builder.append("用户问题：").append(requestDTO.getMessage().trim());
        return builder.toString();
    }

    private String buildFallbackReply(String userMessage, List<AssistantActionDTO> actions, boolean missingApiKey) {
        StringBuilder builder = new StringBuilder();
        if (containsAny(userMessage, "优惠券", "领券", "秒杀")) {
            builder.append("你可以先去优惠券页面领取可用的券，再到订单页完成支付、核销或退款。");
        } else if (containsAny(userMessage, "订单", "支付", "退款", "核销")) {
            builder.append("订单相关操作已经接到订单页里了，你可以先打开“我的订单”，根据状态继续支付、取消、核销或退款。");
        } else if (containsAny(userMessage, "笔记", "发布", "评论", "blog")) {
            builder.append("如果你想发笔记，可以先去发布页；如果是查看别人的笔记，详情页已经支持评论、回复和点赞。");
        } else if (containsAny(userMessage, "消息", "通知")) {
            builder.append("消息中心适合查看店铺动态和提醒；如果你想让助手帮你规划下一步，也可以继续描述你的目标。");
        } else if (containsAny(userMessage, "搜索", "附近", "店", "美食", "火锅", "咖啡", "ktv")) {
            builder.append("你可以告诉我想找什么类型的店、预算和距离要求，我会帮你整理成更明确的找店建议。");
        } else {
            builder.append("我可以帮你梳理找店、领券、下单、发笔记、查看消息这些操作。你可以直接说出想做的事情，例如“帮我找附近评分高的火锅”或“我想发布一篇探店笔记”。");
        }

        if (missingApiKey) {
            builder.append("\n\n当前还没有配置 DeepSeek API Key，所以我先用本地兜底助手回答。配置 `DEEPSEEK_API_KEY` 后，这里会自动切换到 DeepSeek 对话能力。");
        } else {
            builder.append("\n\nDeepSeek 接口暂时不可用，我已经先切换到本地兜底助手，避免你现在无法继续操作。");
        }

        if (actions != null && !actions.isEmpty()) {
            builder.append("\n\n你也可以直接点下面的快捷入口继续。");
        }
        return builder.toString();
    }

    private List<AssistantActionDTO> buildActions(String userMessage) {
        List<AssistantActionDTO> actions = new ArrayList<>();

        if (containsAny(userMessage, "优惠券", "领券", "秒杀")) {
            actions.add(action("去领优惠券", "打开可领取优惠券页面", "/pages/misc/vouchers.html"));
            actions.add(action("查看订单", "查看券订单状态", "/pages/order/orders.html"));
        }
        if (containsAny(userMessage, "订单", "支付", "退款", "核销")) {
            actions.add(action("我的订单", "继续支付、退款或核销", "/pages/order/orders.html"));
        }
        if (containsAny(userMessage, "笔记", "发布", "探店", "评论", "blog")) {
            actions.add(action("发布笔记", "去发布新的探店内容", "/pages/blog/blog-edit.html"));
        }
        if (containsAny(userMessage, "消息", "通知")) {
            actions.add(action("消息中心", "查看动态和通知", "/pages/misc/messages.html"));
        }
        if (containsAny(userMessage, "收藏", "浏览历史")) {
            actions.add(action("我的收藏", "查看收藏的店铺和笔记", "/pages/misc/favorites.html"));
            actions.add(action("浏览历史", "查看最近访问记录", "/pages/misc/history.html"));
        }
        if (containsAny(userMessage, "搜索", "附近", "店", "美食", "火锅", "咖啡", "ktv")) {
            actions.add(action("智能搜索", "带着关键词去搜索页", "/pages/misc/search.html?keyword=" + encodeKeyword(userMessage)));
        }

        if (actions.isEmpty()) {
            actions.add(action("回到首页", "先去首页继续浏览", "/pages/index-new.html"));
            actions.add(action("智能搜索", "去搜索页输入你的需求", "/pages/misc/search.html"));
        }
        return actions;
    }

    private AssistantActionDTO action(String label, String description, String path) {
        AssistantActionDTO dto = new AssistantActionDTO();
        dto.setLabel(label);
        dto.setDescription(description);
        dto.setPath(path);
        return dto;
    }

    private boolean containsAny(String text, String... keywords) {
        String lowerCaseText = StrUtil.blankToDefault(text, "").toLowerCase();
        for (String keyword : keywords) {
            if (lowerCaseText.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private String encodeKeyword(String keyword) {
        if (StrUtil.isBlank(keyword)) {
            return "";
        }
        try {
            return URLEncoder.encode(keyword.trim(), StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            return keyword.trim();
        }
    }

    private String normalizeBaseUrl() {
        String normalized = StrUtil.blankToDefault(baseUrl, "https://api.deepseek.com").trim();
        if (normalized.endsWith("/")) {
            return normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
