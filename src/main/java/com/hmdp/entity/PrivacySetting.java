package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户隐私设置实体
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_privacy_setting")
public class PrivacySetting implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户id（主键）
     */
    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;

    /**
     * 是否显示关注列表：0-隐藏 1-公开
     */
    private Integer showFollowing = 1;

    /**
     * 是否显示粉丝列表：0-隐藏 1-公开
     */
    private Integer showFollowers = 1;

    /**
     * 是否显示收藏：0-隐藏 1-公开
     */
    private Integer showFavorites = 1;

    /**
     * 是否显示浏览历史：0-隐藏 1-公开
     */
    private Integer showHistory = 0;

    /**
     * 是否允许陌生人私信：0-不允许 1-允许
     */
    private Integer allowMessage = 1;

    /**
     * 是否允许被推荐：0-不允许 1-允许
     */
    private Integer allowRecommend = 1;

    /**
     * 是否显示在线状态：0-隐藏 1-显示
     */
    private Integer showOnlineStatus = 1;

    /**
     * 隐身访问模式：0-关闭 1-开启（浏览他人主页不留足迹）
     */
    private Integer stealthMode = 0;

    /**
     * 是否允许访客通知：0-不允许 1-允许（有人访问主页时收到提示）
     */
    private Integer allowVisitNotify = 1;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
