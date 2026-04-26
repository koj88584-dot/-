-- 商家店铺资料审核、招牌菜、团购能力迁移脚本
-- 适用于已存在的 hmdp 数据库。重复执行时，新增表使用 IF NOT EXISTS；tb_shop.phone 使用动态检测避免重复添加。

SET @phone_col_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'tb_shop'
    AND COLUMN_NAME = 'phone'
);
SET @phone_sql := IF(
  @phone_col_exists = 0,
  'ALTER TABLE `tb_shop` ADD COLUMN `phone` varchar(32) DEFAULT NULL COMMENT ''店铺联系电话'' AFTER `open_hours`',
  'SELECT 1'
);
PREPARE stmt FROM @phone_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `tb_shop_update_application` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint(20) UNSIGNED NOT NULL COMMENT '提交商家用户id',
  `shop_id` bigint(20) UNSIGNED NOT NULL COMMENT '店铺id',
  `change_payload` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '待审核变更JSON',
  `proof_images` varchar(2048) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '证明图片',
  `message` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '补充说明',
  `status` tinyint(1) UNSIGNED NOT NULL DEFAULT 0 COMMENT '0待审核 1已通过 2已驳回',
  `reviewer_id` bigint(20) UNSIGNED DEFAULT NULL COMMENT '审核人',
  `review_remark` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '审核备注',
  `review_time` timestamp NULL DEFAULT NULL COMMENT '审核时间',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_shop_status` (`shop_id`, `status`) USING BTREE,
  KEY `idx_user_id` (`user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='店铺资料变更申请表';

CREATE TABLE IF NOT EXISTS `tb_shop_featured_dish` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `shop_id` bigint(20) UNSIGNED NOT NULL COMMENT '店铺id',
  `name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '招牌菜名称',
  `description` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '招牌菜描述',
  `image` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '展示图片',
  `price` bigint(10) UNSIGNED DEFAULT NULL COMMENT '参考价，单位分',
  `status` tinyint(1) UNSIGNED NOT NULL DEFAULT 0 COMMENT '0草稿 1上架 2下架',
  `sort` int(11) NOT NULL DEFAULT 0 COMMENT '排序值',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_shop_status_sort` (`shop_id`, `status`, `sort`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='店铺招牌菜表';

CREATE TABLE IF NOT EXISTS `tb_group_deal` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `shop_id` bigint(20) UNSIGNED NOT NULL COMMENT '店铺id',
  `title` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '团购标题',
  `description` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '团购描述',
  `images` varchar(2048) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '团购图片',
  `rules` varchar(2048) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '使用规则',
  `price` bigint(10) UNSIGNED NOT NULL COMMENT '团购价，单位分',
  `original_price` bigint(10) UNSIGNED NOT NULL COMMENT '门市价，单位分',
  `stock` int(11) UNSIGNED NOT NULL DEFAULT 0 COMMENT '库存',
  `sold` int(11) UNSIGNED NOT NULL DEFAULT 0 COMMENT '已售',
  `status` tinyint(1) UNSIGNED NOT NULL DEFAULT 0 COMMENT '0草稿 1上架 2下架 3已结束',
  `begin_time` timestamp NULL DEFAULT NULL COMMENT '开始时间',
  `end_time` timestamp NULL DEFAULT NULL COMMENT '结束时间',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_shop_status` (`shop_id`, `status`) USING BTREE,
  KEY `idx_status_time` (`status`, `begin_time`, `end_time`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='店铺团购表';

CREATE TABLE IF NOT EXISTS `tb_group_deal_order` (
  `id` bigint(20) UNSIGNED NOT NULL COMMENT '主键',
  `deal_id` bigint(20) UNSIGNED NOT NULL COMMENT '团购id',
  `shop_id` bigint(20) UNSIGNED NOT NULL COMMENT '店铺id',
  `user_id` bigint(20) UNSIGNED NOT NULL COMMENT '购买用户id',
  `pay_value` bigint(10) UNSIGNED NOT NULL COMMENT '实付金额，单位分',
  `status` tinyint(1) UNSIGNED NOT NULL DEFAULT 1 COMMENT '1未支付 2已支付待核销 3已核销 4已取消 5退款中 6已退款',
  `verify_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '核销券码',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `pay_time` timestamp NULL DEFAULT NULL COMMENT '支付时间',
  `use_time` timestamp NULL DEFAULT NULL COMMENT '核销时间',
  `cancel_time` timestamp NULL DEFAULT NULL COMMENT '取消时间',
  `refund_time` timestamp NULL DEFAULT NULL COMMENT '退款时间',
  `effective_time` timestamp NULL DEFAULT NULL COMMENT '生效时间',
  `expire_time` timestamp NULL DEFAULT NULL COMMENT '过期时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_verify_code` (`verify_code`) USING BTREE,
  KEY `idx_shop_status` (`shop_id`, `status`) USING BTREE,
  KEY `idx_user_status` (`user_id`, `status`) USING BTREE,
  KEY `idx_deal_id` (`deal_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='团购订单表';
