package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.MerchantApplication;
import com.hmdp.mapper.MerchantApplicationMapper;
import com.hmdp.service.IMerchantApplicationService;
import org.springframework.stereotype.Service;

@Service
public class MerchantApplicationServiceImpl extends ServiceImpl<MerchantApplicationMapper, MerchantApplication> implements IMerchantApplicationService {
}
