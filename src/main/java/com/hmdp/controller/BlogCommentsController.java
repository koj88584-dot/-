package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.service.IBlogCommentsService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/blog-comments")
public class BlogCommentsController {

    @Resource
    private IBlogCommentsService blogCommentsService;

    /**
     * 发表评论
     * @param blogId 博客id
     * @param content 评论内容
     * @param parentId 父评论id（回复评论时使用，可选）
     */
    @PostMapping
    public Result addComment(@RequestParam("blogId") Long blogId,
                             @RequestParam("content") String content,
                             @RequestParam(value = "parentId", required = false) Long parentId) {
        return blogCommentsService.addComment(blogId, content, parentId);
    }

    /**
     * 查询博客评论列表
     * @param blogId 博客id
     * @param current 当前页
     */
    @GetMapping("/list/{blogId}")
    public Result queryComments(@PathVariable("blogId") Long blogId,
                                @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogCommentsService.queryComments(blogId, current);
    }

    /**
     * 点赞评论
     * @param id 评论id
     */
    @PutMapping("/like/{id}")
    public Result likeComment(@PathVariable("id") Long id) {
        return blogCommentsService.likeComment(id);
    }

    /**
     * 删除评论
     * @param id 评论id
     */
    @DeleteMapping("/{id}")
    public Result deleteComment(@PathVariable("id") Long id) {
        return blogCommentsService.deleteComment(id);
    }

    /**
     * 查询评论的回复列表
     * @param parentId 父评论id
     * @param current 当前页
     */
    @GetMapping("/replies/{parentId}")
    public Result queryReplies(@PathVariable("parentId") Long parentId,
                               @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogCommentsService.queryReplies(parentId, current);
    }
}
