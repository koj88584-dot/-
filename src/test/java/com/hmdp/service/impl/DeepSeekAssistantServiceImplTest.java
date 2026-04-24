package com.hmdp.service.impl;

import com.hmdp.dto.AssistantActionDTO;
import com.hmdp.dto.DeepSeekChatRequestDTO;
import com.hmdp.dto.DeepSeekChatResponseDTO;
import com.hmdp.dto.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeepSeekAssistantServiceImplTest {

    private DeepSeekAssistantServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DeepSeekAssistantServiceImpl();
        ReflectionTestUtils.setField(service, "apiKey", "");
        ReflectionTestUtils.setField(service, "baseUrl", "https://api.deepseek.com");
        ReflectionTestUtils.setField(service, "model", "deepseek-chat");
    }

    @Test
    void chatShouldBuildCombinedSearchAndVoucherActions() {
        DeepSeekChatRequestDTO request = new DeepSeekChatRequestDTO();
        request.setMessage("附近咖啡 + 优惠券");

        Result result = service.chat(request);

        assertThat(result.getSuccess()).isTrue();
        DeepSeekChatResponseDTO payload = (DeepSeekChatResponseDTO) result.getData();
        assertThat(payload.getConfigured()).isFalse();
        assertThat(payload.getActions()).hasSizeGreaterThanOrEqualTo(2);

        AssistantActionDTO primary = payload.getActions().get(0);
        assertThat(primary.getType()).isEqualTo("search_shop");
        assertThat(primary.getAutoRun()).isTrue();
        assertThat(primary.getParams())
                .containsEntry("keyword", "咖啡")
                .containsEntry("sortBy", "distance")
                .containsEntry("couponIntent", "true")
                .containsEntry("couponTab", "available");

        AssistantActionDTO secondary = payload.getActions().get(1);
        assertThat(secondary.getType()).isEqualTo("open_vouchers");
        assertThat(secondary.getParams()).containsEntry("tab", "available");
    }

    @Test
    void chatShouldOpenPendingOrdersWhenAsked() {
        DeepSeekChatRequestDTO request = new DeepSeekChatRequestDTO();
        request.setMessage("帮我看一下待支付订单");

        Result result = service.chat(request);

        assertThat(result.getSuccess()).isTrue();
        DeepSeekChatResponseDTO payload = (DeepSeekChatResponseDTO) result.getData();
        List<AssistantActionDTO> actions = payload.getActions();
        assertThat(actions).isNotEmpty();
        assertThat(actions.get(0).getType()).isEqualTo("open_orders");
        assertThat(actions.get(0).getAutoRun()).isTrue();
        assertThat(actions.get(0).getParams()).containsEntry("status", "1");
    }

    @Test
    void chatShouldPrepareBlogDraftAndCompanionSearchAction() {
        DeepSeekChatRequestDTO request = new DeepSeekChatRequestDTO();
        request.setMessage("我想发探店笔记，写一篇咖啡分享");

        Result result = service.chat(request);

        assertThat(result.getSuccess()).isTrue();
        DeepSeekChatResponseDTO payload = (DeepSeekChatResponseDTO) result.getData();
        assertThat(payload.getActions()).hasSize(2);

        AssistantActionDTO blogAction = payload.getActions().get(0);
        assertThat(blogAction.getType()).isEqualTo("create_blog");
        assertThat(blogAction.getAutoRun()).isTrue();
        assertThat(blogAction.getParams())
                .containsEntry("shopKeyword", "咖啡")
                .containsEntry("title", "咖啡探店记录");

        AssistantActionDTO searchAction = payload.getActions().get(1);
        assertThat(searchAction.getType()).isEqualTo("search_shop");
        assertThat(searchAction.getAutoRun()).isFalse();
        assertThat(searchAction.getParams()).containsEntry("keyword", "咖啡");
    }
}
