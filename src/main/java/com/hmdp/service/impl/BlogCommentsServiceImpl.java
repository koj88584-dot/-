package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.BlogCommentDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.BlogComments;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogCommentsMapper;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogCommentsService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements IBlogCommentsService {

    private static final String COMMENT_LIKED_KEY = "comment:liked:";

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private BlogMapper blogMapper;

    @Override
    public Result addComment(Long blogId, String content, Long parentId) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        if (blogId == null) {
            return Result.fail("笔记不存在");
        }
        if (StrUtil.isBlank(content)) {
            return Result.fail("评论内容不能为空");
        }

        Blog blog = blogMapper.selectById(blogId);
        if (blog == null) {
            return Result.fail("笔记不存在");
        }

        Long normalizedParentId = parentId != null ? parentId : 0L;
        if (normalizedParentId > 0) {
            BlogComments parentComment = getById(normalizedParentId);
            if (parentComment == null || !Objects.equals(parentComment.getBlogId(), blogId)) {
                return Result.fail("回复的评论不存在");
            }
        }

        BlogComments comment = new BlogComments();
        comment.setUserId(user.getId());
        comment.setBlogId(blogId);
        comment.setContent(content.trim());
        comment.setParentId(normalizedParentId);
        comment.setAnswerId(normalizedParentId > 0 ? normalizedParentId : 0L);
        comment.setLiked(0);
        comment.setStatus(false);
        comment.setCreateTime(LocalDateTime.now());
        comment.setUpdateTime(LocalDateTime.now());
        save(comment);

        blogMapper.update(
                null,
                Wrappers.<Blog>lambdaUpdate()
                        .eq(Blog::getId, blogId)
                        .setSql("comments = IFNULL(comments, 0) + 1")
        );

        return Result.ok(comment.getId());
    }

    @Override
    public Result queryComments(Long blogId, Integer current) {
        Page<BlogComments> page = lambdaQuery()
                .eq(BlogComments::getBlogId, blogId)
                .eq(BlogComments::getParentId, 0L)
                .eq(BlogComments::getStatus, false)
                .orderByDesc(BlogComments::getLiked)
                .orderByDesc(BlogComments::getCreateTime)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));

        return Result.ok(toCommentDTOs(page.getRecords(), true), page.getTotal());
    }

    @Override
    public Result likeComment(Long id) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }

        BlogComments comment = getById(id);
        if (comment == null) {
            return Result.fail("评论不存在");
        }

        String key = COMMENT_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, user.getId().toString());
        boolean success;
        int likedCount;
        if (score == null) {
            success = update().setSql("liked = liked + 1").eq("id", id).update();
            if (success) {
                stringRedisTemplate.opsForZSet().add(key, user.getId().toString(), System.currentTimeMillis());
            }
            likedCount = comment.getLiked() + 1;
        } else {
            success = update().setSql("liked = CASE WHEN liked > 0 THEN liked - 1 ELSE 0 END").eq("id", id).update();
            if (success) {
                stringRedisTemplate.opsForZSet().remove(key, user.getId().toString());
            }
            likedCount = Math.max(comment.getLiked() - 1, 0);
        }

        if (!success) {
            return Result.fail("点赞失败");
        }
        return Result.ok(likedCount);
    }

    @Override
    public Result deleteComment(Long id) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }

        BlogComments comment = getById(id);
        if (comment == null) {
            return Result.fail("评论不存在");
        }
        if (!Objects.equals(comment.getUserId(), user.getId())) {
            return Result.fail("无权删除这条评论");
        }

        Long replyCount = lambdaQuery()
                .eq(BlogComments::getParentId, id)
                .eq(BlogComments::getStatus, false)
                .count();

        removeById(id);
        lambdaUpdate().eq(BlogComments::getParentId, id).remove();

        long totalRemoved = 1 + (replyCount == null ? 0 : replyCount);
        blogMapper.update(
                null,
                Wrappers.<Blog>lambdaUpdate()
                        .eq(Blog::getId, comment.getBlogId())
                        .setSql("comments = GREATEST(IFNULL(comments, 0) - " + totalRemoved + ", 0)")
        );

        return Result.ok();
    }

    @Override
    public Result queryReplies(Long parentId, Integer current) {
        Page<BlogComments> page = lambdaQuery()
                .eq(BlogComments::getParentId, parentId)
                .eq(BlogComments::getStatus, false)
                .orderByAsc(BlogComments::getCreateTime)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));

        return Result.ok(toCommentDTOs(page.getRecords(), false), page.getTotal());
    }

    private List<BlogCommentDTO> toCommentDTOs(List<BlogComments> comments, boolean includeReplyTotal) {
        if (comments == null || comments.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, User> userMap = loadUsers(comments);
        Long currentUserId = getCurrentUserId();
        Set<Long> likedCommentIds = currentUserId == null
                ? Collections.emptySet()
                : comments.stream()
                .map(BlogComments::getId)
                .filter(commentId -> stringRedisTemplate.opsForZSet().score(COMMENT_LIKED_KEY + commentId, currentUserId.toString()) != null)
                .collect(Collectors.toSet());

        return comments.stream().map(comment -> {
            BlogCommentDTO dto = BeanUtil.copyProperties(comment, BlogCommentDTO.class);
            User commentUser = userMap.get(comment.getUserId());
            dto.setUserName(commentUser != null ? commentUser.getNickName() : "匿名用户");
            dto.setUserIcon(commentUser != null ? commentUser.getIcon() : "/imgs/icons/default-icon.png");
            dto.setOwner(currentUserId != null && Objects.equals(currentUserId, comment.getUserId()));
            dto.setLikedByCurrentUser(likedCommentIds.contains(comment.getId()));
            if (includeReplyTotal) {
                dto.setReplyTotal(lambdaQuery()
                        .eq(BlogComments::getParentId, comment.getId())
                        .eq(BlogComments::getStatus, false)
                        .count());
            } else {
                dto.setReplyTotal(0L);
            }
            return dto;
        }).collect(Collectors.toList());
    }

    private Map<Long, User> loadUsers(List<BlogComments> comments) {
        List<Long> userIds = comments.stream()
                .map(BlogComments::getUserId)
                .distinct()
                .collect(Collectors.toList());
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (left, right) -> left));
    }

    private Long getCurrentUserId() {
        UserDTO user = UserHolder.getUser();
        return user == null ? null : user.getId();
    }
}
