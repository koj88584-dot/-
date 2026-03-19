package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.BlogComments;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogCommentsMapper;
import com.hmdp.service.IBlogCommentsService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements IBlogCommentsService {

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final String COMMENT_LIKED_KEY = "comment:liked:";

    @Override
    public Result addComment(Long blogId, String content, Long parentId) {
        Long userId = UserHolder.getUser().getId();
        
        BlogComments comment = new BlogComments();
        comment.setUserId(userId);
        comment.setBlogId(blogId);
        comment.setContent(content);
        comment.setParentId(parentId != null ? parentId : 0L);
        comment.setAnswerId(0L);
        comment.setLiked(0);
        comment.setStatus(false);
        comment.setCreateTime(LocalDateTime.now());
        comment.setUpdateTime(LocalDateTime.now());
        
        save(comment);
        return Result.ok(comment.getId());
    }

    @Override
    public Result queryComments(Long blogId, Integer current) {
        // 查询一级评论
        Page<BlogComments> page = lambdaQuery()
                .eq(BlogComments::getBlogId, blogId)
                .eq(BlogComments::getParentId, 0L)
                .eq(BlogComments::getStatus, false)
                .orderByDesc(BlogComments::getLiked)
                .orderByDesc(BlogComments::getCreateTime)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        
        List<BlogComments> comments = page.getRecords();
        
        // 填充用户信息
        fillUserInfo(comments);
        
        // 填充是否点赞
        fillIsLiked(comments);
        
        return Result.ok(comments, page.getTotal());
    }

    @Override
    public Result likeComment(Long id) {
        Long userId = UserHolder.getUser().getId();
        String key = COMMENT_LIKED_KEY + id;
        
        // 判断是否已点赞
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        
        BlogComments comment = getById(id);
        if (comment == null) {
            return Result.fail("评论不存在");
        }
        
        if (score == null) {
            // 未点赞，可以点赞
            boolean success = update().setSql("liked = liked + 1").eq("id", id).update();
            if (success) {
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        } else {
            // 已点赞，取消点赞
            boolean success = update().setSql("liked = liked - 1").eq("id", id).update();
            if (success) {
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        
        return Result.ok();
    }

    @Override
    public Result deleteComment(Long id) {
        Long userId = UserHolder.getUser().getId();
        
        BlogComments comment = getById(id);
        if (comment == null) {
            return Result.fail("评论不存在");
        }
        
        // 只能删除自己的评论
        if (!comment.getUserId().equals(userId)) {
            return Result.fail("无权删除此评论");
        }
        
        // 删除评论及其所有回复
        removeById(id);
        lambdaUpdate().eq(BlogComments::getParentId, id).remove();
        
        return Result.ok();
    }

    @Override
    public Result queryReplies(Long parentId, Integer current) {
        Page<BlogComments> page = lambdaQuery()
                .eq(BlogComments::getParentId, parentId)
                .eq(BlogComments::getStatus, false)
                .orderByAsc(BlogComments::getCreateTime)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        
        List<BlogComments> replies = page.getRecords();
        
        // 填充用户信息
        fillUserInfo(replies);
        
        // 填充是否点赞
        fillIsLiked(replies);
        
        return Result.ok(replies, page.getTotal());
    }

    private void fillUserInfo(List<BlogComments> comments) {
        if (comments == null || comments.isEmpty()) {
            return;
        }
        
        // 批量查询用户信息
        List<Long> userIds = comments.stream()
                .map(BlogComments::getUserId)
                .distinct()
                .collect(Collectors.toList());
        
        List<User> users = userService.listByIds(userIds);
        Map<Long, User> userMap = users.stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        
        // 注意：BlogComments实体没有用户昵称和头像字段，这里不做处理
        // 如果需要，可以返回DTO对象
    }

    private void fillIsLiked(List<BlogComments> comments) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return;
        }
        
        for (BlogComments comment : comments) {
            String key = COMMENT_LIKED_KEY + comment.getId();
            Double score = stringRedisTemplate.opsForZSet().score(key, user.getId().toString());
            // 注意：BlogComments实体没有isLiked字段，这里不做处理
        }
    }
}
