CREATE TABLE IF NOT EXISTS `tb_user_role` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint(20) UNSIGNED NOT NULL COMMENT '用户id',
  `role_code` varchar(32) NOT NULL COMMENT '角色编码: USER/MERCHANT/ADMIN',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_role`(`user_id`, `role_code`) USING BTREE,
  INDEX `idx_role_code`(`role_code`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `tb_shop_member` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `shop_id` bigint(20) UNSIGNED NOT NULL COMMENT '店铺id',
  `user_id` bigint(20) UNSIGNED NOT NULL COMMENT '用户id',
  `role_code` varchar(32) NOT NULL DEFAULT 'OWNER' COMMENT '店铺角色: OWNER/MANAGER/CLERK',
  `status` tinyint(1) UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态: 1启用 0停用',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_shop_user`(`shop_id`, `user_id`) USING BTREE,
  INDEX `idx_shop_status`(`shop_id`, `status`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO `tb_user_role` (`user_id`, `role_code`) VALUES
  (1, 'USER'),
  (1, 'MERCHANT'),
  (1, 'ADMIN');

INSERT INTO `tb_shop_member` (`shop_id`, `user_id`, `role_code`, `status`)
VALUES
  (1, 1, 'OWNER', 1),
  (10, 1, 'OWNER', 1)
ON DUPLICATE KEY UPDATE
  `role_code` = VALUES(`role_code`),
  `status` = VALUES(`status`);

-- 按你的实际账号授权时，可参考下面两句：
-- INSERT IGNORE INTO `tb_user_role` (`user_id`, `role_code`) VALUES (你的用户ID, 'USER'), (你的用户ID, 'MERCHANT');
-- INSERT INTO `tb_shop_member` (`shop_id`, `user_id`, `role_code`, `status`) VALUES (店铺ID, 你的用户ID, 'OWNER', 1)
-- ON DUPLICATE KEY UPDATE `role_code` = VALUES(`role_code`), `status` = VALUES(`status`);

CREATE TABLE IF NOT EXISTS `tb_merchant_application` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) UNSIGNED NOT NULL,
  `contact_name` varchar(64) NOT NULL,
  `contact_phone` varchar(32) NOT NULL,
  `company_name` varchar(128) DEFAULT NULL,
  `description` varchar(1024) DEFAULT NULL,
  `proof_images` varchar(1024) NOT NULL,
  `status` tinyint(1) NOT NULL DEFAULT 0 COMMENT '0待审核 1已通过 2已驳回',
  `reviewer_id` bigint(20) UNSIGNED DEFAULT NULL,
  `review_remark` varchar(512) DEFAULT NULL,
  `review_time` timestamp NULL DEFAULT NULL,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_merchant_application_user`(`user_id`, `status`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `tb_shop_claim_application` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) UNSIGNED NOT NULL,
  `shop_id` bigint(20) UNSIGNED NOT NULL,
  `merchant_application_id` bigint(20) UNSIGNED DEFAULT NULL,
  `proof_images` varchar(1024) NOT NULL,
  `message` varchar(1024) DEFAULT NULL,
  `status` tinyint(1) NOT NULL DEFAULT 0 COMMENT '0待审核 1已通过 2已驳回',
  `reviewer_id` bigint(20) UNSIGNED DEFAULT NULL,
  `review_remark` varchar(512) DEFAULT NULL,
  `review_time` timestamp NULL DEFAULT NULL,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_shop_claim_user`(`user_id`, `status`) USING BTREE,
  INDEX `idx_shop_claim_shop`(`shop_id`, `status`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `tb_shop_create_application` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) UNSIGNED NOT NULL,
  `merchant_application_id` bigint(20) UNSIGNED DEFAULT NULL,
  `shop_name` varchar(128) NOT NULL,
  `type_id` bigint(20) UNSIGNED NOT NULL,
  `address` varchar(255) NOT NULL,
  `x` double DEFAULT NULL,
  `y` double DEFAULT NULL,
  `contact_phone` varchar(32) NOT NULL,
  `images` varchar(1024) DEFAULT NULL,
  `proof_images` varchar(1024) NOT NULL,
  `status` tinyint(1) NOT NULL DEFAULT 0 COMMENT '0待审核 1已通过 2已驳回',
  `reviewer_id` bigint(20) UNSIGNED DEFAULT NULL,
  `review_remark` varchar(512) DEFAULT NULL,
  `review_time` timestamp NULL DEFAULT NULL,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_shop_create_user`(`user_id`, `status`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
