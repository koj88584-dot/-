package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.AssistantActionDTO;
import com.hmdp.dto.AssistantMessageDTO;
import com.hmdp.dto.DeepSeekChatRequestDTO;
import com.hmdp.dto.DeepSeekChatResponseDTO;
import com.hmdp.dto.Result;
import com.hmdp.service.IDeepSeekAssistantService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Service
public class DeepSeekAssistantServiceImpl implements IDeepSeekAssistantService {

    @Value("${deepseek.api-key:}")
    private String apiKey;

    @Value("${deepseek.base-url:https://fufu.iqach.top/v1}")
    private String baseUrl;

    @Value("${deepseek.model:mimo-v2.5-pro}")
    private String model;

    @Override
    public Result chat(DeepSeekChatRequestDTO requestDTO) {
        if (requestDTO == null || StrUtil.isBlank(requestDTO.getMessage())) {
            return Result.fail("\u8BF7\u8F93\u5165\u4F60\u60F3\u54A8\u8BE2\u7684\u95EE\u9898");
        }

        String userMessage = requestDTO.getMessage().trim();
        AssistantRoutingSupport.AssistantIntent intent = AssistantRoutingSupport.analyzeIntent(userMessage);
        List<AssistantActionDTO> actions = AssistantRoutingSupport.buildActions(intent);
        String routedReply = AssistantRoutingSupport.buildRoutedReply(intent, actions);

        DeepSeekChatResponseDTO responseDTO = new DeepSeekChatResponseDTO();
        responseDTO.setActions(actions);

        try {
            String effectiveKey = StrUtil.blankToDefault(apiKey, "sk-no-key-required");
            String aiReply = callAI(requestDTO, routedReply, effectiveKey);
            responseDTO.setReply(StrUtil.blankToDefault(aiReply, routedReply));
            responseDTO.setConfigured(true);
            return Result.ok(responseDTO);
        } catch (Exception e) {
            log.warn("AI assistant call failed, falling back to routed reply. Error: {}", e.getMessage(), e);
            responseDTO.setConfigured(false);
            responseDTO.setReply(routedReply);
            return Result.ok(responseDTO);
        }
    }

    private String callAI(DeepSeekChatRequestDTO requestDTO, String routedReply, String effectiveKey) throws Exception {
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
                .set("content", buildUserPrompt(requestDTO, routedReply)));

        JSONObject body = JSONUtil.createObj()
                .set("model", model)
                .set("messages", messages)
                .set("stream", false)
                .set("temperature", 0.4);

        String endpoint = normalizeBaseUrl() + "/chat/completions";
        log.info("AI request: model={}, endpoint={}, msgCount={}", model, endpoint, messages.size());

        // Use standard Java HttpURLConnection instead of hutool HttpRequest
        // to avoid proxy/SNI issues with hutool
        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Authorization", "Bearer " + effectiveKey);
        conn.setDoOutput(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);

        byte[] bodyBytes = body.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(bodyBytes);
            os.flush();
        }

        int statusCode = conn.getResponseCode();
        log.info("AI response status={}", statusCode);

        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(
                        statusCode >= 400 ? conn.getErrorStream() : conn.getInputStream(),
                        StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        }

        String responseText = sb.toString();
        log.info("AI response received, length={}", responseText.length());

        if (statusCode >= 400) {
            log.error("AI API returned HTTP {}: {}", statusCode, responseText);
            throw new IllegalStateException("AI API returned HTTP " + statusCode);
        }

        JSONObject response = JSONUtil.parseObj(responseText);
        if (response.containsKey("error")) {
            String errorMsg = response.getByPath("error.message", String.class);
            log.error("AI API returned error: {}", errorMsg);
            throw new IllegalStateException(errorMsg);
        }

        JSONArray choices = response.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("AI did not return valid results");
        }

        JSONObject firstChoice = choices.getJSONObject(0);
        JSONObject message = firstChoice.getJSONObject("message");
        if (message == null || StrUtil.isBlank(message.getStr("content"))) {
            throw new IllegalStateException("AI returned empty content");
        }
        String content = message.getStr("content").trim();
        log.info("AI reply: {}", content.length() > 100 ? content.substring(0, 100) + "..." : content);
        return content;
    }

    private String buildSystemPrompt() {
        return "\u4F60\u662F HMDP \u672C\u5730\u751F\u6D3B\u5E94\u7528\u5185\u7684\u667A\u80FD\u52A9\u624B\u3002"
                + "\u8BF7\u7528\u7B80\u6D01\u81EA\u7136\u7684\u4E2D\u6587\u76F4\u63A5\u56DE\u590D\u7528\u6237\uFF0C\u8BED\u6C14\u50CF\u4E00\u4E2A\u4F1A\u5E2E\u7528\u6237\u529E\u4E8B\u7684\u5E94\u7528\u5185\u52A9\u7406\u3002"
                + "\u5982\u679C\u7CFB\u7EDF\u5DF2\u7ECF\u7ED9\u51FA\u4E86\u660E\u786E\u7684\u4E0B\u4E00\u6B65\u52A8\u4F5C\uFF0C\u8BF7\u4F18\u5148\u987A\u7740\u8FD9\u4E2A\u52A8\u4F5C\u56DE\u590D\u3002"
                + "\u4E0D\u8981\u8F93\u51FA Markdown \u6807\u9898\uFF0C\u4E0D\u8981\u7F16\u9020\u4E0D\u5B58\u5728\u7684\u9875\u9762\u548C\u529F\u80FD\uFF0C\u4E5F\u4E0D\u8981\u8F93\u51FA JSON\u3002";
    }

    private String buildUserPrompt(DeepSeekChatRequestDTO requestDTO, String routedReply) {
        StringBuilder builder = new StringBuilder();
        if (StrUtil.isNotBlank(requestDTO.getScene())) {
            builder.append("\u5F53\u524D\u9875\u9762\u573A\u666F\uFF1A").append(requestDTO.getScene().trim()).append('\n');
        }
        builder.append("\u7528\u6237\u95EE\u9898\uFF1A").append(requestDTO.getMessage().trim()).append('\n');
        builder.append("\u5EFA\u8BAE\u56DE\u590D\u65B9\u5411\uFF1A").append(routedReply).append('\n');
        builder.append("\u8BF7\u7528\u4E00\u53E5\u7B80\u6D01\u4E2D\u6587\u56DE\u7B54\uFF0C\u4E0D\u8981\u8D85\u8FC7\u4E24\u53E5\u8BDD\u3002");
        return builder.toString();
    }

    private String normalizeBaseUrl() {
        String normalized = StrUtil.blankToDefault(baseUrl, "https://fufu.iqach.top/v1").trim();
        if (normalized.endsWith("/")) {
            return normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
