package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.BlogComments;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogCommentsService extends IService<BlogComments> {

    /**
     * 发表评论
     *
     * @param blogId   博客id
     * @param content  评论内容
     * @param parentId 父评论id（回复评论时使用）
     * @return {@link Result}
     */
    Result addComment(Long blogId, String content, Long parentId);

    /**
     * 查询博客评论列表
     *
     * @param blogId  博客id
     * @param current 当前页
     * @return {@link Result}
     */
    Result queryComments(Long blogId, Integer current);

    /**
     * 点赞评论
     *
     * @param id 评论id
     * @return {@link Result}
     */
    Result likeComment(Long id);

    /**
     * 删除评论
     *
     * @param id 评论id
     * @return {@link Result}
     */
    Result deleteComment(Long id);

    /**
     * 查询评论的回复列表
     *
     * @param parentId 父评论id
     * @param current  当前页
     * @return {@link Result}
     */
    Result queryReplies(Long parentId, Integer current);
}
