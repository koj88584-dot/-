package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.ShopCreateApplication;
import com.hmdp.mapper.ShopCreateApplicationMapper;
import com.hmdp.service.IShopCreateApplicationService;
import org.springframework.stereotype.Service;

@Service
public class ShopCreateApplicationServiceImpl extends ServiceImpl<ShopCreateApplicationMapper, ShopCreateApplication> implements IShopCreateApplicationService {
}
