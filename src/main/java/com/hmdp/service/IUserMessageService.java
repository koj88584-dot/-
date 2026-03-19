package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.UserMessage;

/**
 * 用户消息服务接口
 */
public interface IUserMessageService extends IService<UserMessage> {

    /**
     * 获取用户消息列表
     * @param current 当前页
     * @param type 消息类型，不传则查全部
     * @return 消息列表
     */
    Result queryMessages(Integer current, Integer type);

    /**
     * 获取未读消息数量
     * @return 未读数量
     */
    Result getUnreadCount();

    /**
     * 标记消息为已读
     * @param messageId 消息id，不传则标记所有为已读
     * @return 结果
     */
    Result markAsRead(Long messageId);

    /**
     * 删除消息
     * @param messageId 消息id
     * @return 结果
     */
    Result deleteMessage(Long messageId);

    /**
     * 清空所有消息
     * @return 结果
     */
    Result clearAllMessages();

    /**
     * 推送店铺更新消息给收藏用户
     * @param shopId 店铺id
     * @param type 消息类型
     * @param title 标题
     * @param content 内容
     * @param images 图片
     * @return 结果
     */
    Result pushShopUpdateMessage(Long shopId, Integer type, String title, String content, String images);
}
