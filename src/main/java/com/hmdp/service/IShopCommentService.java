package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopComment;

import java.util.List;

/**
 * <p>
 * 店铺评价表 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopCommentService extends IService<ShopComment> {

    /**
     * 查询店铺评价列表
     * @param shopId 店铺id
     * @param current 页码
     * @param size 每页大小
     * @return 评价列表
     */
    Result queryCommentList(Long shopId, Integer current, Integer size);

    /**
     * 添加评价
     * @param comment 评价信息
     * @return 结果
     */
    Result addComment(ShopComment comment);

    /**
     * 点赞评价
     * @param commentId 评价id
     * @return 结果
     */
    Result likeComment(Long commentId);
}
