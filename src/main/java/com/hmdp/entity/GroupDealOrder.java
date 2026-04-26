package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_group_deal_order")
public class GroupDealOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    private Long dealId;

    private Long shopId;

    private Long userId;

    private Long payValue;

    private Integer status;

    private String verifyCode;

    /**
     * 0 未评价，1 已评价。用于约束“核销后才能评价，且一单只能评价一次”。
     */
    private Integer commented;

    private LocalDateTime createTime;

    private LocalDateTime payTime;

    private LocalDateTime useTime;

    private LocalDateTime cancelTime;

    private LocalDateTime refundTime;

    private LocalDateTime effectiveTime;

    private LocalDateTime expireTime;

    private LocalDateTime updateTime;
}
