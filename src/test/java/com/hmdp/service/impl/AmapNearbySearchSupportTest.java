package com.hmdp.service.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AmapNearbySearchSupportTest {

    @Test
    void buildRequestShouldNormalizeInputsAndFallbackKeyword() {
        AmapNearbySearchSupport.NearbySearchRequest request = AmapNearbySearchSupport.buildRequest(
                "", 1, "  ", 121.47, 31.23, 100000, 0, 99
        );

        assertThat(request.isMissingKey()).isTrue();
        assertThat(request.getKeyword()).isEqualTo("美食");
        assertThat(request.getRadius()).isEqualTo(50000);
        assertThat(request.getPage()).isEqualTo(1);
        assertThat(request.getPageSize()).isEqualTo(25);
        assertThat(request.getTypes()).isEqualTo("050000");
        assertThat(request.getLocation()).isEqualTo("121.47,31.23");
        assertThat(request.getUrl()).contains("radius=50000").contains("offset=25").contains("page=1");
    }

    @Test
    void buildRequestShouldResolveTypesByShopType() {
        AmapNearbySearchSupport.NearbySearchRequest request = AmapNearbySearchSupport.buildRequest(
                "demo-key", 8, "酒吧", 121.47, 31.23, 3000, 2, 10
        );

        assertThat(request.isMissingKey()).isFalse();
        assertThat(request.getTypes()).isEqualTo("080304");
        assertThat(request.getKeyword()).isEqualTo("酒吧");
        assertThat(AmapNearbySearchSupport.requestSummary(request))
                .contains("typeId=8")
                .contains("keyword=酒吧")
                .contains("page=2");
    }

    @Test
    void parseTotalHitsShouldFallbackWhenCountIsBlankOrInvalid() {
        assertThat(AmapNearbySearchSupport.parseTotalHits(null, 3)).isEqualTo(3L);
        assertThat(AmapNearbySearchSupport.parseTotalHits("", 5)).isEqualTo(5L);
        assertThat(AmapNearbySearchSupport.parseTotalHits("NaN", 7)).isEqualTo(7L);
        assertThat(AmapNearbySearchSupport.parseTotalHits("12", 1)).isEqualTo(12L);
    }

    @Test
    void hasMoreShouldReflectCurrentPageWindow() {
        assertThat(AmapNearbySearchSupport.hasMore(41L, 2, 20)).isTrue();
        assertThat(AmapNearbySearchSupport.hasMore(40L, 2, 20)).isFalse();
        assertThat(AmapNearbySearchSupport.hasMore(0L, 1, 20)).isFalse();
    }
}
