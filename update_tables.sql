-- 添加缺少的数据库表

-- ----------------------------
-- Table structure for tb_favorites
-- 收藏夹表
-- ----------------------------
DROP TABLE IF EXISTS `tb_favorites`;
CREATE TABLE `tb_favorites` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint(20) UNSIGNED NOT NULL COMMENT '用户id',
  `target_id` bigint(20) UNSIGNED NOT NULL COMMENT '目标id（店铺id或博客id）',
  `type` tinyint(1) UNSIGNED NOT NULL COMMENT '收藏类型：1-店铺 2-博客',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_user_target_type` (`user_id`, `target_id`, `type`) USING BTREE,
  KEY `idx_user_id` (`user_id`) USING BTREE,
  KEY `idx_target_id` (`target_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户收藏表' ROW_FORMAT = Compact;

-- ----------------------------
-- Table structure for tb_browse_history
-- 浏览历史表
-- ----------------------------
DROP TABLE IF EXISTS `tb_browse_history`;
CREATE TABLE `tb_browse_history` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint(20) UNSIGNED NOT NULL COMMENT '用户id',
  `target_id` bigint(20) UNSIGNED NOT NULL COMMENT '目标id（店铺id或博客id）',
  `type` tinyint(1) UNSIGNED NOT NULL COMMENT '类型：1-店铺 2-博客',
  `browse_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '浏览时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_user_id` (`user_id`) USING BTREE,
  KEY `idx_browse_time` (`browse_time`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '浏览历史表' ROW_FORMAT = Compact;

-- ----------------------------
-- Table structure for tb_user_message
-- 用户消息表
-- ----------------------------
DROP TABLE IF EXISTS `tb_user_message`;
CREATE TABLE `tb_user_message` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint(20) UNSIGNED NOT NULL COMMENT '用户id',
  `shop_id` bigint(20) UNSIGNED DEFAULT NULL COMMENT '店铺id',
  `type` tinyint(1) UNSIGNED NOT NULL COMMENT '消息类型：1-优惠活动 2-新品上架 3-店铺公告 4-价格变动',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '消息标题',
  `content` varchar(2048) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '消息内容',
  `images` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '相关图片',
  `is_read` tinyint(1) UNSIGNED DEFAULT 0 COMMENT '是否已读：0-未读 1-已读',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `read_time` timestamp NULL DEFAULT NULL COMMENT '读取时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_user_id` (`user_id`) USING BTREE,
  KEY `idx_is_read` (`is_read`) USING BTREE,
  KEY `idx_create_time` (`create_time`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户消息表' ROW_FORMAT = Compact;

-- ----------------------------
-- Table structure for tb_shop_update_message
-- 店铺更新消息表（用于推送消息给收藏用户）
-- ----------------------------
DROP TABLE IF EXISTS `tb_shop_update_message`;
CREATE TABLE `tb_shop_update_message` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `shop_id` bigint(20) UNSIGNED NOT NULL COMMENT '店铺id',
  `type` tinyint(1) UNSIGNED NOT NULL COMMENT '消息类型：1-优惠活动 2-新品上架 3-店铺公告 4-价格变动',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '消息标题',
  `content` varchar(2048) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '消息内容',
  `images` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '相关图片',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `is_pushed` tinyint(1) UNSIGNED DEFAULT 0 COMMENT '是否已推送：0-未推送 1-已推送',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_shop_id` (`shop_id`) USING BTREE,
  KEY `idx_is_pushed` (`is_pushed`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '店铺更新消息表' ROW_FORMAT = Compact;

-- ----------------------------
-- Table structure for tb_privacy_setting
-- 隐私设置表
-- ----------------------------
DROP TABLE IF EXISTS `tb_privacy_setting`;
CREATE TABLE `tb_privacy_setting` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint(20) UNSIGNED NOT NULL COMMENT '用户id',
  `setting_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '设置项key',
  `setting_value` tinyint(1) UNSIGNED DEFAULT 1 COMMENT '设置值：0-关闭 1-开启',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_user_key` (`user_id`, `setting_key`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '隐私设置表' ROW_FORMAT = Compact;

-- ----------------------------
-- Table structure for tb_voucher_order
-- 优惠券订单表
-- ----------------------------
DROP TABLE IF EXISTS `tb_voucher_order`;
CREATE TABLE `tb_voucher_order` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint(20) UNSIGNED NOT NULL COMMENT '用户id',
  `voucher_id` bigint(20) UNSIGNED NOT NULL COMMENT '优惠券id',
  `voucher_title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '优惠券标题（冗余）',
  `voucher_sub_title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '优惠券副标题（冗余）',
  `voucher_images` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '优惠券图片（冗余）',
  `pay_value` bigint(10) UNSIGNED NOT NULL COMMENT '支付金额，单位是分',
  `actual_value` bigint(10) UNSIGNED NOT NULL COMMENT '抵扣金额，单位是分',
  `status` tinyint(1) UNSIGNED DEFAULT 1 COMMENT '订单状态：1-未支付 2-已支付 3-已核销 4-已取消 5-退款中 6-已退款',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `pay_time` timestamp NULL DEFAULT NULL COMMENT '支付时间',
  `verify_time` timestamp NULL DEFAULT NULL COMMENT '核销时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_user_id` (`user_id`) USING BTREE,
  KEY `idx_voucher_id` (`voucher_id`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '优惠券订单表' ROW_FORMAT = Compact;
