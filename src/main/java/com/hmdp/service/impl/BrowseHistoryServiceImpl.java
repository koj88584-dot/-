package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.entity.BrowseHistory;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.BrowseHistoryMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IBrowseHistoryService;
import com.hmdp.service.IShopService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BrowseHistoryServiceImpl extends ServiceImpl<BrowseHistoryMapper, BrowseHistory> implements IBrowseHistoryService {

    @Autowired
    @Lazy
    private IShopService shopService;

    @Autowired
    @Lazy
    private IBlogService blogService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final String HISTORY_KEY = "history:";

    @Override
    public Result addHistory(Integer type, Long targetId) {
        Long userId = UserHolder.getUser().getId();

        remove(new LambdaQueryWrapper<BrowseHistory>()
                .eq(BrowseHistory::getUserId, userId)
                .eq(BrowseHistory::getType, type)
                .eq(BrowseHistory::getTargetId, targetId));

        BrowseHistory history = new BrowseHistory();
        history.setUserId(userId);
        history.setType(type);
        history.setTargetId(targetId);
        history.setBrowseTime(LocalDateTime.now());
        save(history);

        String key = HISTORY_KEY + userId + ":" + type;
        stringRedisTemplate.opsForZSet().add(key, targetId.toString(), System.currentTimeMillis());
        stringRedisTemplate.opsForZSet().removeRange(key, 0, -101);

        return Result.ok();
    }

    @Override
    public Result queryHistory(Integer type, Integer current) {
        Long userId = UserHolder.getUser().getId();

        LambdaQueryWrapper<BrowseHistory> wrapper = new LambdaQueryWrapper<BrowseHistory>()
                .eq(BrowseHistory::getUserId, userId)
                .orderByDesc(BrowseHistory::getBrowseTime);
        if (type != null) {
            wrapper.eq(BrowseHistory::getType, type);
        }

        Page<BrowseHistory> page = page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE), wrapper);
        List<BrowseHistory> records = page.getRecords();
        if (records.isEmpty()) {
            return Result.ok(new ArrayList<>());
        }

        List<Object> result = new ArrayList<>();
        for (BrowseHistory history : records) {
            if (history.getType() == 1) {
                Shop shop = shopService.getById(history.getTargetId());
                if (shop != null) {
                    result.add(buildHistoryItem(history, shop));
                }
            } else if (history.getType() == 2) {
                Blog blog = blogService.getById(history.getTargetId());
                if (blog != null) {
                    result.add(buildHistoryItem(history, blog));
                }
            }
        }

        return Result.ok(result);
    }

    @Override
    public Result clearHistory(Integer type) {
        Long userId = UserHolder.getUser().getId();

        LambdaQueryWrapper<BrowseHistory> wrapper = new LambdaQueryWrapper<BrowseHistory>()
                .eq(BrowseHistory::getUserId, userId);

        if (type != null) {
            wrapper.eq(BrowseHistory::getType, type);
            stringRedisTemplate.delete(HISTORY_KEY + userId + ":" + type);
        } else {
            stringRedisTemplate.delete(HISTORY_KEY + userId + ":1");
            stringRedisTemplate.delete(HISTORY_KEY + userId + ":2");
        }

        remove(wrapper);
        return Result.ok();
    }

    @Override
    public Result deleteHistory(Long id) {
        Long userId = UserHolder.getUser().getId();

        boolean removed = remove(new LambdaQueryWrapper<BrowseHistory>()
                .eq(BrowseHistory::getId, id)
                .eq(BrowseHistory::getUserId, userId));

        return removed ? Result.ok() : Result.fail("删除失败");
    }

    private Map<String, Object> buildHistoryItem(BrowseHistory history, Object target) {
        Map<String, Object> item = new HashMap<>(BeanUtil.beanToMap(target));
        item.put("historyId", history.getId());
        item.put("historyType", history.getType());
        item.put("browseTime", history.getBrowseTime());
        return item;
    }
}
