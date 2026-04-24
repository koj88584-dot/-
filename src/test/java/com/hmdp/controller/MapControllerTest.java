package com.hmdp.controller;

import com.hmdp.config.AmapConfig;
import com.hmdp.dto.Result;
import com.hmdp.service.IShopService;
import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class MapControllerTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private IShopService shopService;

    private MapController controller;
    private AmapConfig amapConfig;

    @BeforeEach
    void setUp() {
        controller = new MapController();
        amapConfig = new AmapConfig();
        ReflectionTestUtils.setField(controller, "stringRedisTemplate", stringRedisTemplate);
        ReflectionTestUtils.setField(controller, "shopService", shopService);
        ReflectionTestUtils.setField(controller, "amapConfig", amapConfig);
    }

    @Test
    void amapConfigEndpointShouldExposeFrontendKeys() {
        amapConfig.setWebKey("web-key");
        amapConfig.setSecurityJsCode("security-code");

        Result result = controller.getAmapWebConfig();

        assertThat(result.getSuccess()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertThat(data)
                .containsEntry("webKey", "web-key")
                .containsEntry("securityJsCode", "security-code")
                .containsEntry("configured", true);
    }

    @Test
    void amapProxyEndpointsShouldGracefullyReturnEmptyDataWhenServerKeyMissing() {
        amapConfig.setKey("");

        Result regeo = controller.reverseGeocode(112.9388D, 28.2282D);
        Result placeText = controller.searchPlaceText("coffee", 10, 1, true, null);

        assertThat(regeo.getSuccess()).isTrue();
        assertThat(regeo.getData()).isEqualTo(Collections.emptyMap());

        assertThat(placeText.getSuccess()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) placeText.getData();
        assertThat(data.get("count")).isEqualTo(0);
        assertThat((List<?>) data.get("pois")).isEmpty();
    }

    @Test
    void regeoPayloadShouldExposeDistrictAndAdcode() {
        Map<String, Object> data = MapController.buildRegeoPayload(JSONUtil.parseObj("{"
                + "\"formatted_address\":\"Hunan Changsha Yuelu\","
                + "\"addressComponent\":{"
                + "\"province\":\"Hunan\","
                + "\"city\":\"Changsha\","
                + "\"district\":\"Yuelu\","
                + "\"adcode\":\"430104\""
                + "}"
                + "}"));

        assertThat(data)
                .containsEntry("province", "Hunan")
                .containsEntry("city", "Changsha")
                .containsEntry("district", "Yuelu")
                .containsEntry("adcode", "430104")
                .containsEntry("formattedAddress", "Hunan Changsha Yuelu");
    }
}
