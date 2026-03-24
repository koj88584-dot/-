package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 店铺评价表
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_shop_comment")
public class ShopComment implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 店铺id（支持负数，高德地图店铺）
     */
    private Long shopId;

    /**
     * 用户id
     */
    private Long userId;

    /**
     * 评分，1~5分，乘10保存
     */
    private Integer score;

    /**
     * 口味评分
     */
    private Integer tasteScore;

    /**
     * 环境评分
     */
    private Integer envScore;

    /**
     * 服务评分
     */
    private Integer serviceScore;

    /**
     * 评价内容
     */
    private String content;

    /**
     * 评价图片，多张以","隔开
     */
    private String images;

    /**
     * 点赞数
     */
    private Integer liked;

    /**
     * 状态：0正常，1隐藏
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 用户信息（非数据库字段）
     */
    private String userName;
    private String userIcon;
    private String userLevel;
}
