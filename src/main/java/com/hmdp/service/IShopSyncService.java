package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;

/**
 * 店铺同步服务接口
 */
public interface IShopSyncService {

    Shop syncToLocal(Shop amapShop);

    Shop queryByAmapPoiId(String amapPoiId);

    boolean isSynced(Long amapShopId);

    Shop getOrSync(Shop amapShop);

    Shop ensureMappedShop(Shop amapShop);

    void refreshMirrorAsync(Shop amapShop);

    Result triggerSync(Long shopId, String action);

    Result batchSync(Integer typeId, String cityCode, String keywords);
}
