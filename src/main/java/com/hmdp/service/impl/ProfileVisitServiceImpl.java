package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.PrivacySetting;
import com.hmdp.entity.ProfileVisit;
import com.hmdp.entity.User;
import com.hmdp.entity.UserMessage;
import com.hmdp.mapper.ProfileVisitMapper;
import com.hmdp.service.IPrivacySettingService;
import com.hmdp.service.IProfileVisitService;
import com.hmdp.service.IUserMessageService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ProfileVisitServiceImpl extends ServiceImpl<ProfileVisitMapper, ProfileVisit> implements IProfileVisitService {

    private static final String USER_MESSAGE_COUNT_KEY = "message:count:";

    @Resource
    private IPrivacySettingService privacySettingService;

    @Resource
    private IUserService userService;

    @Resource
    private IUserMessageService userMessageService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional
    public Result recordVisit(Long visitedId) {
        UserDTO visitor = UserHolder.getUser();
        if (visitor == null) {
            return Result.fail("请先登录");
        }
        if (visitedId == null || visitor.getId().equals(visitedId)) {
            return Result.ok();
        }
        if (userService.getById(visitedId) == null) {
            return Result.fail("被访问用户不存在");
        }

        PrivacySetting visitorPrivacy = privacySettingService.getUserPrivacySetting(visitor.getId());
        boolean stealth = visitorPrivacy != null
                && visitorPrivacy.getStealthMode() != null
                && visitorPrivacy.getStealthMode() == 1;

        if (stealth) {
            Map<String, Object> data = new HashMap<>();
            data.put("recorded", false);
            data.put("stealth", true);
            return Result.ok(data);
        }

        ProfileVisit visit = new ProfileVisit();
        visit.setVisitorId(visitor.getId());
        visit.setVisitedId(visitedId);
        visit.setVisitTime(LocalDateTime.now());
        visit.setIsStealth(0);
        visit.setIsRead(0);
        save(visit);

        PrivacySetting visitedPrivacy = privacySettingService.getUserPrivacySetting(visitedId);
        boolean allowNotify = visitedPrivacy == null
                || visitedPrivacy.getAllowVisitNotify() == null
                || visitedPrivacy.getAllowVisitNotify() == 1;
        if (allowNotify) {
            pushVisitMessage(visitedId, visitor);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("recorded", true);
        data.put("stealth", false);
        return Result.ok(data);
    }

    @Override
    public Result queryMyVisitors(Integer current) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }

        Page<ProfileVisit> page = lambdaQuery()
                .eq(ProfileVisit::getVisitedId, user.getId())
                .eq(ProfileVisit::getIsStealth, 0)
                .orderByDesc(ProfileVisit::getVisitTime)
                .page(new Page<>(current == null ? 1 : current, SystemConstants.MAX_PAGE_SIZE));
        List<ProfileVisit> visits = page.getRecords();
        if (visits.isEmpty()) {
            return Result.ok(Collections.emptyList(), page.getTotal());
        }

        List<Long> visitorIds = visits.stream()
                .map(ProfileVisit::getVisitorId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, User> userMap = userService.listByIds(visitorIds).stream()
                .collect(Collectors.toMap(User::getId, item -> item, (a, b) -> a));

        List<Map<String, Object>> rows = visits.stream().map(visit -> {
            User visitor = userMap.get(visit.getVisitorId());
            Map<String, Object> row = new HashMap<>();
            row.put("id", visit.getId());
            row.put("visitorId", visit.getVisitorId());
            row.put("visitorName", visitor == null ? "用户" + visit.getVisitorId() : visitor.getNickName());
            row.put("visitorIcon", visitor == null ? "" : visitor.getIcon());
            row.put("visitTime", visit.getVisitTime());
            row.put("isRead", visit.getIsRead());
            return row;
        }).collect(Collectors.toList());

        markAllRead();
        return Result.ok(rows, page.getTotal());
    }

    @Override
    public Result countUnread() {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        Long count = lambdaQuery()
                .eq(ProfileVisit::getVisitedId, user.getId())
                .eq(ProfileVisit::getIsStealth, 0)
                .eq(ProfileVisit::getIsRead, 0)
                .count();
        return Result.ok(count);
    }

    @Override
    public Result markAllRead() {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        lambdaUpdate()
                .eq(ProfileVisit::getVisitedId, user.getId())
                .eq(ProfileVisit::getIsRead, 0)
                .set(ProfileVisit::getIsRead, 1)
                .update();
        return Result.ok();
    }

    private void pushVisitMessage(Long visitedId, UserDTO visitor) {
        UserMessage message = new UserMessage();
        message.setUserId(visitedId);
        message.setType(3);
        message.setTitle("有人访问了你的主页");
        message.setContent((visitor.getNickName() == null ? "一位用户" : visitor.getNickName()) + " 刚刚看过你的主页");
        message.setIsRead(0);
        message.setCreateTime(LocalDateTime.now());
        userMessageService.save(message);
        stringRedisTemplate.delete(USER_MESSAGE_COUNT_KEY + visitedId);
    }
}
