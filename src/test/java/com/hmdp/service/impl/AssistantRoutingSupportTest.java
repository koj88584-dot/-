package com.hmdp.service.impl;

import com.hmdp.dto.AssistantActionDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantRoutingSupportTest {

    @Test
    void buildActionsShouldKeepSearchAndVoucherIntentTogether() {
        AssistantRoutingSupport.AssistantIntent intent = AssistantRoutingSupport.analyzeIntent("附近咖啡 + 优惠券");

        List<AssistantActionDTO> actions = AssistantRoutingSupport.buildActions(intent);

        assertThat(actions).hasSize(2);
        assertThat(actions.get(0).getType()).isEqualTo("search_shop");
        assertThat(actions.get(0).getAutoRun()).isTrue();
        assertThat(actions.get(0).getParams())
                .containsEntry("keyword", "咖啡")
                .containsEntry("sortBy", "distance")
                .containsEntry("couponIntent", "true")
                .containsEntry("couponTab", "available");
        assertThat(actions.get(1).getType()).isEqualTo("open_vouchers");
        assertThat(actions.get(1).getParams()).containsEntry("tab", "available");
    }

    @Test
    void buildRoutedReplyShouldDescribePendingOrderShortcut() {
        AssistantRoutingSupport.AssistantIntent intent = AssistantRoutingSupport.analyzeIntent("帮我看一下待支付订单");

        List<AssistantActionDTO> actions = AssistantRoutingSupport.buildActions(intent);
        String reply = AssistantRoutingSupport.buildRoutedReply(intent, actions);

        assertThat(actions.get(0).getType()).isEqualTo("open_orders");
        assertThat(actions.get(0).getParams()).containsEntry("status", "1");
        assertThat(reply).contains("待支付订单");
    }
}
