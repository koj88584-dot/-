package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Favorites;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.FavoritesMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IFavoritesService;
import com.hmdp.service.IShopService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 收藏夹服务实现
 */
@Service
public class FavoritesServiceImpl extends ServiceImpl<FavoritesMapper, Favorites> implements IFavoritesService {

    @Resource
    private IShopService shopService;

    @Resource
    private IBlogService blogService;

    @Override
    public Result addFavorite(Integer type, Long targetId) {
        Long userId = UserHolder.getUser().getId();
        
        // 检查是否已收藏
        Long count = lambdaQuery()
                .eq(Favorites::getUserId, userId)
                .eq(Favorites::getType, type)
                .eq(Favorites::getTargetId, targetId)
                .count();
        
        if (count > 0) {
            return Result.fail("已经收藏过了");
        }
        
        // 添加收藏
        Favorites favorites = new Favorites();
        favorites.setUserId(userId);
        favorites.setType(type);
        favorites.setTargetId(targetId);
        favorites.setCreateTime(LocalDateTime.now());
        save(favorites);

        if (type != null && type == 1) {
            shopService.ensureShopExists(targetId);
        }
        
        return Result.ok();
    }

    @Override
    public Result removeFavorite(Integer type, Long targetId) {
        Long userId = UserHolder.getUser().getId();
        
        boolean removed = remove(new LambdaQueryWrapper<Favorites>()
                .eq(Favorites::getUserId, userId)
                .eq(Favorites::getType, type)
                .eq(Favorites::getTargetId, targetId));
        
        return removed ? Result.ok() : Result.fail("取消收藏失败");
    }

    @Override
    public Result isFavorite(Integer type, Long targetId) {
        Long userId = UserHolder.getUser().getId();
        
        Long count = lambdaQuery()
                .eq(Favorites::getUserId, userId)
                .eq(Favorites::getType, type)
                .eq(Favorites::getTargetId, targetId)
                .count();
        
        return Result.ok(count > 0);
    }

    @Override
    public Result toggleFavorite(Integer type, Long targetId) {
        Long userId = UserHolder.getUser().getId();

        Long count = lambdaQuery()
                .eq(Favorites::getUserId, userId)
                .eq(Favorites::getType, type)
                .eq(Favorites::getTargetId, targetId)
                .count();

        if (count > 0) {
            remove(new LambdaQueryWrapper<Favorites>()
                    .eq(Favorites::getUserId, userId)
                    .eq(Favorites::getType, type)
                    .eq(Favorites::getTargetId, targetId));
            return Result.ok(false);
        }

        Favorites favorites = new Favorites();
        favorites.setUserId(userId);
        favorites.setType(type);
        favorites.setTargetId(targetId);
        favorites.setCreateTime(LocalDateTime.now());
        save(favorites);

        if (type != null && type == 1) {
            shopService.ensureShopExists(targetId);
        }

        return Result.ok(true);
    }

    @Override
    public Result queryFavorites(Integer type, Integer current) {
        Long userId = UserHolder.getUser().getId();
        
        // 分页查询收藏记录
        Page<Favorites> page = lambdaQuery()
                .eq(Favorites::getUserId, userId)
                .eq(type != null, Favorites::getType, type)
                .orderByDesc(Favorites::getCreateTime)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        
        List<Favorites> records = page.getRecords();
        if (records.isEmpty()) {
            return Result.ok(new ArrayList<>());
        }
        
        // 根据类型获取详细信息
        List<Object> result = new ArrayList<>();
        for (Favorites fav : records) {
            if (fav.getType() == 1) {
                // 店铺
                Shop shop = shopService.getById(fav.getTargetId());
                if (shop == null) {
                    shop = shopService.ensureShopExists(fav.getTargetId());
                }
                if (shop != null) {
                    result.add(shop);
                }
            } else if (fav.getType() == 2) {
                // 博客
                Blog blog = blogService.getById(fav.getTargetId());
                if (blog != null) {
                    result.add(blog);
                }
            }
        }
        
        return Result.ok(result);
    }
}
