package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.GroupDeal;
import com.hmdp.mapper.GroupDealMapper;
import com.hmdp.service.IGroupDealService;
import org.springframework.stereotype.Service;

@Service
public class GroupDealServiceImpl extends ServiceImpl<GroupDealMapper, GroupDeal> implements IGroupDealService {
}
