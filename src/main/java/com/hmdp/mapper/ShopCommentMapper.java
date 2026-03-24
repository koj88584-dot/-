package com.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.entity.ShopComment;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * <p>
 * 店铺评价表 Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface ShopCommentMapper extends BaseMapper<ShopComment> {

    /**
     * 查询店铺评价列表（带用户信息）
     */
    @Select("SELECT c.*, u.nick_name as user_name, u.icon as user_icon " +
            "FROM tb_shop_comment c " +
            "LEFT JOIN tb_user u ON c.user_id = u.id " +
            "WHERE c.shop_id = #{shopId} AND c.status = 0 " +
            "ORDER BY c.create_time DESC")
    List<ShopComment> queryCommentListWithUser(@Param("shopId") Long shopId);
}
