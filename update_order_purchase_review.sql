ALTER TABLE `tb_group_deal_order`
  ADD COLUMN `commented` tinyint(1) UNSIGNED NOT NULL DEFAULT 0 COMMENT '0 not reviewed, 1 reviewed' AFTER `verify_code`;

CREATE TABLE IF NOT EXISTS `tb_shop_featured_dish_order` (
  `id` bigint(20) UNSIGNED NOT NULL COMMENT 'primary key',
  `dish_id` bigint(20) UNSIGNED NOT NULL COMMENT 'featured dish id',
  `shop_id` bigint(20) UNSIGNED NOT NULL COMMENT 'shop id',
  `user_id` bigint(20) UNSIGNED NOT NULL COMMENT 'buyer user id',
  `pay_value` bigint(10) UNSIGNED NOT NULL COMMENT 'paid amount in cents',
  `status` tinyint(1) UNSIGNED NOT NULL DEFAULT 1 COMMENT '1 unpaid 2 paid pending verify 3 verified 4 cancelled 5 refunding 6 refunded',
  `verify_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'verification code',
  `commented` tinyint(1) UNSIGNED NOT NULL DEFAULT 0 COMMENT '0 not reviewed, 1 reviewed',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `pay_time` timestamp NULL DEFAULT NULL COMMENT 'pay time',
  `use_time` timestamp NULL DEFAULT NULL COMMENT 'verify time',
  `cancel_time` timestamp NULL DEFAULT NULL COMMENT 'cancel time',
  `refund_time` timestamp NULL DEFAULT NULL COMMENT 'refund time',
  `effective_time` timestamp NULL DEFAULT NULL COMMENT 'effective time',
  `expire_time` timestamp NULL DEFAULT NULL COMMENT 'expire time',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_verify_code` (`verify_code`) USING BTREE,
  KEY `idx_shop_status` (`shop_id`, `status`) USING BTREE,
  KEY `idx_user_status` (`user_id`, `status`) USING BTREE,
  KEY `idx_dish_id` (`dish_id`) USING BTREE,
  KEY `idx_user_comment` (`user_id`, `shop_id`, `status`, `commented`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='featured dish order table';
