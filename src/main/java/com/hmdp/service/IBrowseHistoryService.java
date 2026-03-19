package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.BrowseHistory;

/**
 * 浏览历史服务接口
 */
public interface IBrowseHistoryService extends IService<BrowseHistory> {

    /**
     * 添加浏览记录
     *
     * @param type     浏览类型：1-店铺 2-博客
     * @param targetId 目标id
     * @return {@link Result}
     */
    Result addHistory(Integer type, Long targetId);

    /**
     * 查询浏览历史
     *
     * @param type    浏览类型：1-店铺 2-博客，为空则查全部
     * @param current 当前页
     * @return {@link Result}
     */
    Result queryHistory(Integer type, Integer current);

    /**
     * 清空浏览历史
     *
     * @param type 浏览类型：1-店铺 2-博客，为空则清空全部
     * @return {@link Result}
     */
    Result clearHistory(Integer type);

    /**
     * 删除单条浏览记录
     *
     * @param id 记录id
     * @return {@link Result}
     */
    Result deleteHistory(Long id);
}
