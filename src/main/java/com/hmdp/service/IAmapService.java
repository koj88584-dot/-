package com.hmdp.service;

import com.hmdp.dto.AmapSearchResultDTO;
import com.hmdp.dto.LocationContextDTO;
import com.hmdp.entity.Shop;

import java.util.List;

/**
 * 高德地图服务接口
 */
public interface IAmapService {

    List<Shop> searchNearbyShops(Integer typeId, Double x, Double y, Integer radius);

    List<Shop> searchNearbyShops(Integer typeId, String keyword, Double x, Double y, Integer radius);

    List<Shop> searchNearbyShops(Integer typeId, String keyword, Double x, Double y, Integer radius,
                                 Integer page, Integer pageSize);

    AmapSearchResultDTO searchNearbyShopsWithMeta(Integer typeId, String keyword, Double x, Double y,
                                                  Integer radius, Integer page, Integer pageSize);

    AmapSearchResultDTO searchTextShopsWithMeta(Integer typeId, String keyword, Double x, Double y,
                                                Integer page, Integer pageSize);

    LocationContextDTO reverseGeocode(Double longitude, Double latitude, Integer accuracy, String source);

    LocationContextDTO locateByIp(String ip);

    Shop convertToShop(Object amapData, Integer typeId);
}
