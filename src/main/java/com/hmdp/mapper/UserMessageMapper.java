package com.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.entity.UserMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户消息 Mapper 接口
 */
@Mapper
public interface UserMessageMapper extends BaseMapper<UserMessage> {
}
