package com.hmdp.service;

import com.hmdp.entity.Shop;

import java.util.List;

/**
 * 高德地图服务接口
 */
public interface IAmapService {

    /**
     * 根据类型和坐标搜索周边店铺
     *
     * @param typeId 店铺类型ID
     * @param x      经度
     * @param y      纬度
     * @param radius 搜索半径（米）
     * @return 店铺列表
     */
    List<Shop> searchNearbyShops(Integer typeId, Double x, Double y, Integer radius);

    /**
     * 根据关键词和坐标搜索周边店铺
     *
     * @param typeId 店铺类型ID
     * @param keyword 搜索关键词
     * @param x      经度
     * @param y      纬度
     * @param radius 搜索半径（米）
     * @return 店铺列表
     */
    List<Shop> searchNearbyShops(Integer typeId, String keyword, Double x, Double y, Integer radius);

    /**
     * 将高德地图数据转换为店铺实体
     *
     * @param amapData 高德地图返回的POI数据
     * @param typeId   店铺类型ID
     * @return 店铺实体
     */
    Shop convertToShop(Object amapData, Integer typeId);
}
