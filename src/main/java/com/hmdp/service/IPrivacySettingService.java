package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.PrivacySetting;

/**
 * 隐私设置服务接口
 */
public interface IPrivacySettingService extends IService<PrivacySetting> {

    /**
     * 获取当前用户的隐私设置
     *
     * @return {@link Result}
     */
    Result getMyPrivacySetting();

    /**
     * 更新隐私设置
     *
     * @param setting 隐私设置
     * @return {@link Result}
     */
    Result updatePrivacySetting(PrivacySetting setting);

    /**
     * 获取指定用户的隐私设置（用于判断是否可见）
     *
     * @param userId 用户id
     * @return {@link PrivacySetting}
     */
    PrivacySetting getUserPrivacySetting(Long userId);

    /**
     * 检查是否可以查看某用户的关注列表
     *
     * @param userId 用户id
     * @return 是否可见
     */
    boolean canViewFollowing(Long userId);

    /**
     * 检查是否可以查看某用户的粉丝列表
     *
     * @param userId 用户id
     * @return 是否可见
     */
    boolean canViewFollowers(Long userId);

    /**
     * 检查是否可以查看某用户的收藏
     *
     * @param userId 用户id
     * @return 是否可见
     */
    boolean canViewFavorites(Long userId);
}
