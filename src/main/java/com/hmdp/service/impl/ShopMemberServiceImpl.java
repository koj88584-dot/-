package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.ShopMember;
import com.hmdp.mapper.ShopMemberMapper;
import com.hmdp.service.IShopMemberService;
import org.springframework.stereotype.Service;

@Service
public class ShopMemberServiceImpl extends ServiceImpl<ShopMemberMapper, ShopMember> implements IShopMemberService {
}
