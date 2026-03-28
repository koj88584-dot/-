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
     * 鏍规嵁绫诲瀷鍜屽潗鏍囨悳绱㈠懆杈瑰簵閾伙紝鏀寔鎸囧畾鏍囬〉鍜屾瘡椤垫潯鏁?
     *
     * @param typeId 搴楅摵绫诲瀷ID
     * @param keyword 鎼滅储鍏抽敭璇?
     * @param x      缁忓害
     * @param y      绾害
     * @param radius 鎼滅储鍗婂緞锛堢背锛?
     * @param page   绗嚑椤?1涓鸿捣濮?
     * @param pageSize 姣忛〉鏉℃暟
     * @return 搴楅摵鍒楄〃
     */
    List<Shop> searchNearbyShops(Integer typeId, String keyword, Double x, Double y, Integer radius, Integer page, Integer pageSize);

    /**
     * 将高德地图数据转换为店铺实体
     *
     * @param amapData 高德地图返回的POI数据
     * @param typeId   店铺类型ID
     * @return 店铺实体
     */
    Shop convertToShop(Object amapData, Integer typeId);
}
