package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.entity.PrivacySetting;
import com.hmdp.service.IPrivacySettingService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * 隐私设置控制器
 */
@RestController
@RequestMapping("/privacy")
public class PrivacySettingController {

    @Resource
    private IPrivacySettingService privacySettingService;

    /**
     * 获取当前用户的隐私设置
     */
    @GetMapping
    public Result getMyPrivacySetting() {
        return privacySettingService.getMyPrivacySetting();
    }

    /**
     * 更新隐私设置
     */
    @PutMapping
    public Result updatePrivacySetting(@RequestBody PrivacySetting setting) {
        return privacySettingService.updatePrivacySetting(setting);
    }

    /**
     * 检查是否可以查看某用户的关注列表
     */
    @GetMapping("/check/following/{userId}")
    public Result canViewFollowing(@PathVariable("userId") Long userId) {
        return Result.ok(privacySettingService.canViewFollowing(userId));
    }

    /**
     * 检查是否可以查看某用户的粉丝列表
     */
    @GetMapping("/check/followers/{userId}")
    public Result canViewFollowers(@PathVariable("userId") Long userId) {
        return Result.ok(privacySettingService.canViewFollowers(userId));
    }

    /**
     * 检查是否可以查看某用户的收藏
     */
    @GetMapping("/check/favorites/{userId}")
    public Result canViewFavorites(@PathVariable("userId") Long userId) {
        return Result.ok(privacySettingService.canViewFavorites(userId));
    }

    @GetMapping("/public/{userId}")
    public Result getPublicPrivacy(@PathVariable("userId") Long userId) {
        PrivacySetting setting = privacySettingService.getUserPrivacySetting(userId);
        Map<String, Object> data = new HashMap<>();
        data.put("showFollowing", setting.getShowFollowing() == null || setting.getShowFollowing() == 1);
        data.put("showFollowers", setting.getShowFollowers() == null || setting.getShowFollowers() == 1);
        data.put("showFavorites", setting.getShowFavorites() == null || setting.getShowFavorites() == 1);
        data.put("showHistory", setting.getShowHistory() != null && setting.getShowHistory() == 1);
        data.put("allowMessage", setting.getAllowMessage() == null || setting.getAllowMessage() == 1);
        data.put("allowRecommend", setting.getAllowRecommend() == null || setting.getAllowRecommend() == 1);
        data.put("showOnlineStatus", setting.getShowOnlineStatus() == null || setting.getShowOnlineStatus() == 1);
        return Result.ok(data);
    }
}
