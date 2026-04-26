package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.service.IFavoritesService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 收藏夹控制器
 */
@RestController
@RequestMapping("/favorites")
public class FavoritesController {

    @Resource
    private IFavoritesService favoritesService;

    /**
     * 添加收藏
     * @param type 收藏类型：1-店铺 2-博客
     * @param targetId 目标id
     */
    @PostMapping("/{type}/{targetId}")
    public Result addFavorite(@PathVariable("type") Integer type,
                              @PathVariable("targetId") Long targetId) {
        return favoritesService.addFavorite(type, targetId);
    }

    /**
     * 取消收藏
     * @param type 收藏类型：1-店铺 2-博客
     * @param targetId 目标id
     */
    @DeleteMapping("/{type}/{targetId}")
    public Result removeFavorite(@PathVariable("type") Integer type,
                                 @PathVariable("targetId") Long targetId) {
        return favoritesService.removeFavorite(type, targetId);
    }

    /**
     * 查询是否已收藏
     * @param type 收藏类型：1-店铺 2-博客
     * @param targetId 目标id
     */
    @GetMapping("/is/{type}/{targetId}")
    public Result isFavorite(@PathVariable("type") Integer type,
                             @PathVariable("targetId") Long targetId) {
        return favoritesService.isFavorite(type, targetId);
    }

    @PostMapping("/blog/{blogId}")
    public Result toggleBlogFavorite(@PathVariable("blogId") Long blogId) {
        return favoritesService.toggleFavorite(2, blogId);
    }

    @GetMapping("/blog/is/{blogId}")
    public Result isBlogFavorite(@PathVariable("blogId") Long blogId) {
        return favoritesService.isFavorite(2, blogId);
    }

    /**
     * 查询收藏列表
     * @param type 收藏类型：1-店铺 2-博客，不传则查全部
     * @param current 当前页
     */
    @GetMapping("/list")
    public Result queryFavorites(@RequestParam(value = "type", required = false) Integer type,
                                 @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return favoritesService.queryFavorites(type, current);
    }
}
