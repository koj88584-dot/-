package com.hmdp.service.impl;

import com.hmdp.entity.Shop;
import com.hmdp.utils.SystemConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

final class ShopNearbyQuerySupport {

    private ShopNearbyQuerySupport() {
    }

    static NearbyQuerySpec buildSpec(Integer current, int[] searchRadii) {
        int pageSize = SystemConstants.MAX_PAGE_SIZE;
        int page = current == null || current < 1 ? 1 : current;
        int end = page * pageSize;
        int maxRadius = searchRadii[searchRadii.length - 1];
        return new NearbyQuerySpec(page, pageSize, end, maxRadius);
    }

    static List<Shop> mergeCandidates(NearbyQuerySpec spec,
                                      List<Shop> redisCandidates,
                                      Supplier<List<Shop>> dbSupplier,
                                      Supplier<List<Shop>> amapSupplier) {
        List<Shop> candidates = redisCandidates == null
                ? new ArrayList<>()
                : new ArrayList<>(redisCandidates);
        Set<Long> seen = ShopNearbySupport.collectSeenIds(candidates);

        if (candidates.size() < spec.getEnd()) {
            ShopNearbySupport.mergeUnique(candidates, dbSupplier.get(), seen);
        }
        if (candidates.size() < spec.getEnd()) {
            ShopNearbySupport.mergeUnique(candidates, amapSupplier.get(), seen);
        }

        ShopNearbySupport.sortByDistance(candidates);
        return ShopNearbySupport.page(candidates, spec.getPage(), spec.getPageSize());
    }

    static final class NearbyQuerySpec {
        private final int page;
        private final int pageSize;
        private final int end;
        private final int maxRadius;

        private NearbyQuerySpec(int page, int pageSize, int end, int maxRadius) {
            this.page = page;
            this.pageSize = pageSize;
            this.end = end;
            this.maxRadius = maxRadius;
        }

        int getPage() {
            return page;
        }

        int getPageSize() {
            return pageSize;
        }

        int getEnd() {
            return end;
        }

        int getMaxRadius() {
            return maxRadius;
        }
    }
}
