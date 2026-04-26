package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
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
@RequestMapping("/follow")
public class FollowController {
    @Resource
    private IFollowService followService;
    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id")Long followUserId
            ,@PathVariable("isFollow")Boolean isFollow) {
        return followService.follow(followUserId,isFollow);
    }

    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id")Long followUserId) {
        return followService.isFollow(followUserId);
    }

    @GetMapping("/is-mutual/{id}")
    public Result isMutualFollow(@PathVariable("id") Long userId) {
        return followService.isMutualFollow(userId);
    }

    @GetMapping("/common/{id}")
    public Result followCommons(@PathVariable("id")Long id){
        return followService.followCommons(id);
    }

    /**
     * 查询关注列表
     * @param userId 用户ID，不传则查询当前用户
     * @param current 当前页
     * @return 关注列表
     */
    @GetMapping("/list/{userId}")
    public Result followList(@PathVariable("userId") Long userId,
                             @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return followService.queryFollowList(userId, current);
    }

    /**
     * 查询粉丝列表
     * @param userId 用户ID，不传则查询当前用户
     * @param current 当前页
     * @return 粉丝列表
     */
    @GetMapping("/followers/{userId}")
    public Result followerList(@PathVariable("userId") Long userId,
                               @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return followService.queryFollowerList(userId, current);
    }
}
