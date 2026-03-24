package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopComment;
import com.hmdp.mapper.ShopCommentMapper;
import com.hmdp.service.IShopCommentService;
import com.hmdp.service.IShopSyncService;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 店铺评价表 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class ShopCommentServiceImpl extends ServiceImpl<ShopCommentMapper, ShopComment> implements IShopCommentService {

    @Resource
    private IShopSyncService shopSyncService;

    @Override
    public Result queryCommentList(Long shopId, Integer current, Integer size) {
        if (shopId == null) {
            return Result.fail("店铺id不能为空");
        }
        
        // 默认分页参数
        if (current == null || current < 1) {
            current = 1;
        }
        if (size == null || size < 1) {
            size = 10;
        }
        
        // 查询评价列表
        Page<ShopComment> page = query()
                .eq("shop_id", shopId)
                .eq("status", 0)
                .orderByDesc("create_time")
                .page(new Page<>(current, size));
        
        List<ShopComment> records = page.getRecords();
        
        // 填充用户信息
        for (ShopComment comment : records) {
            // TODO: 可以批量查询用户信息优化性能
            comment.setUserLevel("Lv" + (comment.getUserId() % 10 + 1));
        }
        
        return Result.ok(records);
    }

    @Override
    public Result addComment(ShopComment comment) {
        // 获取当前用户
        Long userId = UserHolder.getUser().getId();
        if (userId == null) {
            return Result.fail("请先登录");
        }

        // 校验参数
        if (comment.getShopId() == null) {
            return Result.fail("店铺id不能为空");
        }
        if (comment.getScore() == null || comment.getScore() < 10 || comment.getScore() > 50) {
            return Result.fail("评分必须在1-5分之间");
        }
        if (comment.getContent() == null || comment.getContent().trim().isEmpty()) {
            return Result.fail("评价内容不能为空");
        }

        // 如果是高德地图店铺（负数ID），先同步到本地
        Long shopId = comment.getShopId();
        if (shopId < 0) {
            Result syncResult = shopSyncService.triggerSync(shopId, "comment");
            if (syncResult.getData() != null) {
                shopId = Long.valueOf(syncResult.getData().toString());
                comment.setShopId(shopId);
            }
        }

        // 设置用户id
        comment.setUserId(userId);
        comment.setStatus(0);
        comment.setLiked(0);

        // 保存评价
        boolean success = save(comment);
        if (!success) {
            return Result.fail("评价失败");
        }

        return Result.ok(comment.getId());
    }

    @Override
    public Result likeComment(Long commentId) {
        if (commentId == null) {
            return Result.fail("评价id不能为空");
        }
        
        // 获取当前用户
        Long userId = UserHolder.getUser().getId();
        if (userId == null) {
            return Result.fail("请先登录");
        }
        
        // 查询评价
        ShopComment comment = getById(commentId);
        if (comment == null) {
            return Result.fail("评价不存在");
        }
        
        // 点赞数+1
        comment.setLiked(comment.getLiked() + 1);
        updateById(comment);
        
        return Result.ok(comment.getLiked());
    }
}
