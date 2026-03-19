package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.Favorites;

/**
 * 收藏夹服务接口
 */
public interface IFavoritesService extends IService<Favorites> {

    /**
     * 添加收藏
     *
     * @param type     收藏类型：1-店铺 2-博客
     * @param targetId 目标id
     * @return {@link Result}
     */
    Result addFavorite(Integer type, Long targetId);

    /**
     * 取消收藏
     *
     * @param type     收藏类型：1-店铺 2-博客
     * @param targetId 目标id
     * @return {@link Result}
     */
    Result removeFavorite(Integer type, Long targetId);

    /**
     * 查询是否已收藏
     *
     * @param type     收藏类型：1-店铺 2-博客
     * @param targetId 目标id
     * @return {@link Result}
     */
    Result isFavorite(Integer type, Long targetId);

    /**
     * 查询收藏列表
     *
     * @param type    收藏类型：1-店铺 2-博客
     * @param current 当前页
     * @return {@link Result}
     */
    Result queryFavorites(Integer type, Integer current);
}
