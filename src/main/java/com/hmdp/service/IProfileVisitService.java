package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.ProfileVisit;

public interface IProfileVisitService extends IService<ProfileVisit> {

    Result recordVisit(Long visitedId);

    Result queryMyVisitors(Integer current);

    Result countUnread();

    Result markAllRead();
}
