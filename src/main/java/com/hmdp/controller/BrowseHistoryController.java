package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.service.IBrowseHistoryService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 浏览历史控制器
 */
@RestController
@RequestMapping("/history")
public class BrowseHistoryController {

    @Resource
    private IBrowseHistoryService browseHistoryService;

    /**
     * 添加浏览记录（通常由前端自动调用）
     * @param type 浏览类型：1-店铺 2-博客
     * @param targetId 目标id
     */
    @PostMapping("/{type}/{targetId}")
    public Result addHistory(@PathVariable("type") Integer type,
                             @PathVariable("targetId") Long targetId) {
        return browseHistoryService.addHistory(type, targetId);
    }

    /**
     * 查询浏览历史
     * @param type 浏览类型：1-店铺 2-博客，不传则查全部
     * @param current 当前页
     */
    @GetMapping("/list")
    public Result queryHistory(@RequestParam(value = "type", required = false) Integer type,
                               @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return browseHistoryService.queryHistory(type, current);
    }

    /**
     * 清空浏览历史
     * @param type 浏览类型：1-店铺 2-博客，不传则清空全部
     */
    @DeleteMapping("/clear")
    public Result clearHistory(@RequestParam(value = "type", required = false) Integer type) {
        return browseHistoryService.clearHistory(type);
    }

    /**
     * 删除单条浏览记录
     * @param id 记录id
     */
    @DeleteMapping("/{id}")
    public Result deleteHistory(@PathVariable("id") Long id) {
        return browseHistoryService.deleteHistory(id);
    }
}
