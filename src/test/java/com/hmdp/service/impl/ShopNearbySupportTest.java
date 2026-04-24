package com.hmdp.service.impl;

import com.hmdp.entity.Shop;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ShopNearbySupportTest {

    @Test
    void fillDefaultsShouldPopulateMissingFields() {
        Shop shop = new Shop();

        ShopNearbySupport.fillDefaults(shop);

        assertThat(shop.getImages()).isEqualTo("/imgs/shop/default.png");
        assertThat(shop.getAddress()).isEqualTo("暂无地址");
        assertThat(shop.getSold()).isEqualTo(0);
        assertThat(shop.getComments()).isEqualTo(0);
        assertThat(shop.getScore()).isEqualTo(40);
        assertThat(shop.getOpenHours()).isEqualTo("09:00-22:00");
        assertThat(shop.getCreateTime()).isNotNull();
        assertThat(shop.getUpdateTime()).isNotNull();
    }

    @Test
    void mergeUniqueShouldAppendOnlyNewShopIds() {
        Shop first = shop(1L, 100D);
        Shop duplicate = shop(1L, 200D);
        Shop second = shop(2L, 50D);
        List<Shop> target = new ArrayList<>(List.of(first));
        Set<Long> seen = ShopNearbySupport.collectSeenIds(target);

        ShopNearbySupport.mergeUnique(target, Arrays.asList(duplicate, second), seen);

        assertThat(target).extracting(Shop::getId).containsExactly(1L, 2L);
        assertThat(seen).containsExactly(1L, 2L);
    }

    @Test
    void pageShouldReturnDistanceOrderedSlice() {
        List<Shop> shops = new ArrayList<>(Arrays.asList(
                shop(1L, 300D),
                shop(2L, 120D),
                shop(3L, 200D),
                shop(4L, 50D)
        ));

        ShopNearbySupport.sortByDistance(shops);
        List<Shop> page = ShopNearbySupport.page(shops, 2, 2);

        assertThat(page).extracting(Shop::getId).containsExactly(3L, 1L);
    }

    @Test
    void deduplicateAndLimitShouldKeepNearestUniqueShops() {
        List<Shop> shops = Arrays.asList(
                shop(1L, 300D),
                shop(2L, 120D),
                shop(1L, 60D),
                shop(3L, 90D)
        );

        List<Shop> result = ShopNearbySupport.deduplicateAndLimit(shops, 2);

        assertThat(result).extracting(Shop::getId).containsExactly(3L, 2L);
    }

    @Test
    void applyDistanceShouldCalculateMetersWhenCoordinatesPresent() {
        Shop shop = new Shop();
        shop.setX(121.4737);
        shop.setY(31.2304);

        ShopNearbySupport.applyDistance(shop, 121.4747, 31.2314);

        assertThat(shop.getDistance()).isNotNull();
        assertThat(shop.getDistance()).isGreaterThan(0D);
    }

    private Shop shop(Long id, Double distance) {
        Shop shop = new Shop();
        shop.setId(id);
        shop.setDistance(distance);
        return shop;
    }
}
