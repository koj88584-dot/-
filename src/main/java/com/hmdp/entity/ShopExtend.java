package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 店铺扩展信息表
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_shop_extend")
public class ShopExtend implements Serializable {

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
     * 数据来源：amap-高德，local-本地
     */
    private String source;

    /**
     * 高德POI ID
     */
    private String amapPoiId;

    /**
     * 商圈
     */
    private String businessArea;

    /**
     * 是否有室内地图
     */
    private Boolean indoorMap;

    /**
     * 高德评分
     */
    private BigDecimal rating;

    /**
     * 人均消费
     */
    private BigDecimal cost;

    /**
     * 停车信息
     */
    private String parking;

    /**
     * 是否有WiFi
     */
    private Boolean wifi;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
