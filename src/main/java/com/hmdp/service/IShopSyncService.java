package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;

/**
 * 店铺同步服务接口
 * 用于将高德地图店铺数据同步到本地数据库
 */
public interface IShopSyncService {

    /**
     * 同步高德地图店铺到本地数据库
     * @param amapShop 高德地图店铺数据（负数ID）
     * @return 同步后的本地店铺（正数ID）
     */
    Shop syncToLocal(Shop amapShop);

    /**
     * 根据高德POI ID查询本地店铺
     * @param amapPoiId 高德POI ID
     * @return 本地店铺，不存在返回null
     */
    Shop queryByAmapPoiId(String amapPoiId);

    /**
     * 判断店铺是否已同步到本地
     * @param amapShopId 高德店铺ID（负数）
     * @return 是否已同步
     */
    boolean isSynced(Long amapShopId);

    /**
     * 获取或同步店铺
     * 如果店铺已同步，返回本地店铺；否则先同步再返回
     * @param amapShop 高德地图店铺数据
     * @return 本地店铺
     */
    Shop getOrSync(Shop amapShop);

    /**
     * 触发店铺同步（用户行为驱动）
     * @param shopId 店铺ID（可以是负数）
     * @param action 用户行为：favorite-收藏, comment-评价, view-浏览
     * @return 结果
     */
    Result triggerSync(Long shopId, String action);

    /**
     * 批量同步店铺（管理员功能）
     * @param typeId 店铺类型
     * @param cityCode 城市编码
     * @param keywords 关键词
     * @return 同步数量
     */
    Result batchSync(Integer typeId, String cityCode, String keywords);
}
