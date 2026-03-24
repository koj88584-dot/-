package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.entity.ShopComment;
import com.hmdp.service.IShopCommentService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 * 店铺评价 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/shop-comment")
public class ShopCommentController {

    @Resource
    private IShopCommentService shopCommentService;

    /**
     * 查询店铺评价列表
     * @param shopId 店铺id
     * @param current 页码
     * @param size 每页大小
     * @return 评价列表
     */
    @GetMapping("/list/{shopId}")
    public Result queryCommentList(
            @PathVariable("shopId") Long shopId,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "size", defaultValue = "10") Integer size) {
        return shopCommentService.queryCommentList(shopId, current, size);
    }

    /**
     * 添加评价
     * @param comment 评价信息
     * @return 结果
     */
    @PostMapping
    public Result addComment(@RequestBody ShopComment comment) {
        return shopCommentService.addComment(comment);
    }

    /**
     * 点赞评价
     * @param commentId 评价id
     * @return 结果
     */
    @PutMapping("/like/{commentId}")
    public Result likeComment(@PathVariable("commentId") Long commentId) {
        return shopCommentService.likeComment(commentId);
    }
}
