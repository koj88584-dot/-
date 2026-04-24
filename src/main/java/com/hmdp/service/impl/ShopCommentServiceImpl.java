package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopComment;
import com.hmdp.mapper.ShopCommentMapper;
import com.hmdp.service.IShopCommentService;
import com.hmdp.service.IShopSyncService;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;

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
        if (current == null || current < 1) {
            current = 1;
        }
        if (size == null || size < 1) {
            size = 10;
        }

        List<ShopComment> allComments = baseMapper.queryCommentListWithUser(shopId);
        if (allComments == null || allComments.isEmpty()) {
            return Result.ok(Collections.emptyList(), 0L);
        }

        long total = allComments.size();
        int fromIndex = Math.max((current - 1) * size, 0);
        if (fromIndex >= allComments.size()) {
            return Result.ok(Collections.emptyList(), total);
        }
        int toIndex = Math.min(fromIndex + size, allComments.size());
        List<ShopComment> records = allComments.subList(fromIndex, toIndex);

        for (ShopComment comment : records) {
            comment.setUserLevel("Lv" + (comment.getUserId() % 10 + 1));
            if (comment.getUserName() == null || comment.getUserName().trim().isEmpty()) {
                comment.setUserName("匿名用户");
            }
            if (comment.getUserIcon() == null || comment.getUserIcon().trim().isEmpty()) {
                comment.setUserIcon("/imgs/icons/default-icon.png");
            }
        }

        return Result.ok(records, total);
    }

    @Override
    public Result addComment(ShopComment comment) {
        Long userId = UserHolder.getUser().getId();
        if (userId == null) {
            return Result.fail("请先登录");
        }
        if (comment.getShopId() == null) {
            return Result.fail("店铺id不能为空");
        }
        if (comment.getScore() == null || comment.getScore() < 10 || comment.getScore() > 50) {
            return Result.fail("评分必须在1到5分之间");
        }
        if (comment.getContent() == null || comment.getContent().trim().isEmpty()) {
            return Result.fail("评价内容不能为空");
        }

        Long shopId = comment.getShopId();
        if (shopId < 0) {
            Result syncResult = shopSyncService.triggerSync(shopId, "comment");
            if (syncResult.getData() != null) {
                shopId = Long.valueOf(syncResult.getData().toString());
                comment.setShopId(shopId);
            }
        }

        comment.setUserId(userId);
        comment.setStatus(0);
        comment.setLiked(0);

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

        Long userId = UserHolder.getUser().getId();
        if (userId == null) {
            return Result.fail("请先登录");
        }

        ShopComment comment = getById(commentId);
        if (comment == null) {
            return Result.fail("评价不存在");
        }

        comment.setLiked(comment.getLiked() + 1);
        updateById(comment);
        return Result.ok(comment.getLiked());
    }
}
