package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.service.IUserMessageService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 用户消息控制器
 */
@RestController
@RequestMapping("/message")
public class UserMessageController {

    @Resource
    private IUserMessageService userMessageService;

    /**
     * 获取消息列表
     * @param current 当前页
     * @param type 消息类型，不传则查全部
     */
    @GetMapping("/list")
    public Result queryMessages(@RequestParam(value = "current", defaultValue = "1") Integer current,
                                @RequestParam(value = "type", required = false) Integer type) {
        return userMessageService.queryMessages(current, type);
    }

    /**
     * 获取未读消息数量
     */
    @GetMapping("/unread-count")
    public Result getUnreadCount() {
        return userMessageService.getUnreadCount();
    }

    /**
     * 标记消息为已读
     * @param messageId 消息id，不传则标记所有为已读
     */
    @PostMapping("/read")
    public Result markAsRead(@RequestParam(value = "messageId", required = false) Long messageId) {
        return userMessageService.markAsRead(messageId);
    }

    /**
     * 删除消息
     * @param messageId 消息id
     */
    @DeleteMapping("/{messageId}")
    public Result deleteMessage(@PathVariable("messageId") Long messageId) {
        return userMessageService.deleteMessage(messageId);
    }

    /**
     * 清空所有消息
     */
    @DeleteMapping("/clear")
    public Result clearAllMessages() {
        return userMessageService.clearAllMessages();
    }

    /**
     * 推送店铺更新消息（管理员/商家接口）
     * @param shopId 店铺id
     * @param type 消息类型：1-优惠活动 2-新品上架 3-店铺公告 4-价格变动
     * @param title 标题
     * @param content 内容
     * @param images 图片
     */
    @PostMapping("/push-shop-update")
    public Result pushShopUpdateMessage(@RequestParam("shopId") Long shopId,
                                        @RequestParam("type") Integer type,
                                        @RequestParam("title") String title,
                                        @RequestParam("content") String content,
                                        @RequestParam(value = "images", required = false) String images) {
        return userMessageService.pushShopUpdateMessage(shopId, type, title, content, images);
    }
}
