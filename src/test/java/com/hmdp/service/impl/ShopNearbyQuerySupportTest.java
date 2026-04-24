package com.hmdp.service.impl;

import com.hmdp.entity.Shop;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ShopNearbyQuerySupportTest {

    @Test
    void buildSpecShouldNormalizePageAndRadius() {
        ShopNearbyQuerySupport.NearbyQuerySpec spec = ShopNearbyQuerySupport.buildSpec(0, new int[]{5000, 10000, 20000});

        assertThat(spec.getPage()).isEqualTo(1);
        assertThat(spec.getPageSize()).isEqualTo(10);
        assertThat(spec.getEnd()).isEqualTo(10);
        assertThat(spec.getMaxRadius()).isEqualTo(20000);
    }

    @Test
    void mergeCandidatesShouldUseDbAndAmapFallbackOnlyWhenNeeded() {
        ShopNearbyQuerySupport.NearbyQuerySpec spec = ShopNearbyQuerySupport.buildSpec(1, new int[]{5000, 10000});
        AtomicInteger dbCalls = new AtomicInteger();
        AtomicInteger amapCalls = new AtomicInteger();

        List<Shop> page = ShopNearbyQuerySupport.mergeCandidates(
                spec,
                List.of(shop(1L, 10D), shop(2L, 20D), shop(3L, 30D)),
                () -> {
                    dbCalls.incrementAndGet();
                    return List.of(shop(4L, 40D), shop(5L, 50D), shop(6L, 60D), shop(7L, 70D));
                },
                () -> {
                    amapCalls.incrementAndGet();
                    return List.of(shop(7L, 70D), shop(8L, 80D), shop(9L, 90D), shop(10L, 100D), shop(11L, 110D));
                }
        );

        assertThat(dbCalls.get()).isEqualTo(1);
        assertThat(amapCalls.get()).isEqualTo(1);
        assertThat(page).extracting(Shop::getId).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
    }

    @Test
    void mergeCandidatesShouldSkipFallbacksWhenRedisAlreadyEnough() {
        ShopNearbyQuerySupport.NearbyQuerySpec spec = ShopNearbyQuerySupport.buildSpec(1, new int[]{5000, 10000});
        AtomicInteger dbCalls = new AtomicInteger();
        AtomicInteger amapCalls = new AtomicInteger();

        List<Shop> page = ShopNearbyQuerySupport.mergeCandidates(
                spec,
                List.of(
                        shop(1L, 10D), shop(2L, 20D), shop(3L, 30D), shop(4L, 40D), shop(5L, 50D),
                        shop(6L, 60D), shop(7L, 70D), shop(8L, 80D), shop(9L, 90D), shop(10L, 100D)
                ),
                () -> {
                    dbCalls.incrementAndGet();
                    return Collections.emptyList();
                },
                () -> {
                    amapCalls.incrementAndGet();
                    return Collections.emptyList();
                }
        );

        assertThat(dbCalls.get()).isZero();
        assertThat(amapCalls.get()).isZero();
        assertThat(page).extracting(Shop::getId).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
    }

    private Shop shop(Long id, Double distance) {
        Shop shop = new Shop();
        shop.setId(id);
        shop.setDistance(distance);
        return shop;
    }
}
