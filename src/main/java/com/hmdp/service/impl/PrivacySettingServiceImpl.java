package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.PrivacySetting;
import com.hmdp.mapper.PrivacySettingMapper;
import com.hmdp.service.IPrivacySettingService;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 隐私设置服务实现
 */
@Service
public class PrivacySettingServiceImpl extends ServiceImpl<PrivacySettingMapper, PrivacySetting> implements IPrivacySettingService {

    @Override
    public Result getMyPrivacySetting() {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        
        PrivacySetting setting = getById(user.getId());
        if (setting == null) {
            // 如果没有设置，返回默认设置
            setting = createDefaultSetting(user.getId());
        }
        
        return Result.ok(setting);
    }

    @Override
    public Result updatePrivacySetting(PrivacySetting setting) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        
        // 确保只能修改自己的设置
        setting.setUserId(user.getId());
        setting.setUpdateTime(LocalDateTime.now());
        
        // 检查是否已有设置
        PrivacySetting existing = getById(user.getId());
        if (existing == null) {
            setting.setCreateTime(LocalDateTime.now());
            save(setting);
        } else {
            updateById(setting);
        }
        
        return Result.ok();
    }

    @Override
    public PrivacySetting getUserPrivacySetting(Long userId) {
        PrivacySetting setting = getById(userId);
        if (setting == null) {
            // 返回默认设置（但不保存）
            setting = new PrivacySetting();
            setting.setUserId(userId);
            setting.setShowFollowing(1);
            setting.setShowFollowers(1);
            setting.setShowFavorites(1);
            setting.setShowHistory(0);
            setting.setAllowMessage(1);
            setting.setAllowRecommend(1);
            setting.setShowOnlineStatus(1);
        }
        return setting;
    }

    @Override
    public boolean canViewFollowing(Long userId) {
        UserDTO currentUser = UserHolder.getUser();
        // 查看自己的，总是可以
        if (currentUser != null && currentUser.getId().equals(userId)) {
            return true;
        }
        
        PrivacySetting setting = getUserPrivacySetting(userId);
        return setting.getShowFollowing() == 1;
    }

    @Override
    public boolean canViewFollowers(Long userId) {
        UserDTO currentUser = UserHolder.getUser();
        // 查看自己的，总是可以
        if (currentUser != null && currentUser.getId().equals(userId)) {
            return true;
        }
        
        PrivacySetting setting = getUserPrivacySetting(userId);
        return setting.getShowFollowers() == 1;
    }

    @Override
    public boolean canViewFavorites(Long userId) {
        UserDTO currentUser = UserHolder.getUser();
        // 查看自己的，总是可以
        if (currentUser != null && currentUser.getId().equals(userId)) {
            return true;
        }
        
        PrivacySetting setting = getUserPrivacySetting(userId);
        return setting.getShowFavorites() == 1;
    }

    /**
     * 创建默认隐私设置
     */
    private PrivacySetting createDefaultSetting(Long userId) {
        PrivacySetting setting = new PrivacySetting();
        setting.setUserId(userId);
        setting.setShowFollowing(1);
        setting.setShowFollowers(1);
        setting.setShowFavorites(1);
        setting.setShowHistory(0);
        setting.setAllowMessage(1);
        setting.setAllowRecommend(1);
        setting.setShowOnlineStatus(1);
        setting.setCreateTime(LocalDateTime.now());
        setting.setUpdateTime(LocalDateTime.now());
        save(setting);
        return setting;
    }
}
