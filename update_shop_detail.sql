-- ============================================
-- 店铺详情功能扩展表
-- 用于支持高德地图店铺详情的完整功能
-- ============================================

-- 1. 店铺评价表（用户真实评价）
DROP TABLE IF EXISTS `tb_shop_comment`;
CREATE TABLE `tb_shop_comment` (
    `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `shop_id` bigint(20) NOT NULL COMMENT '店铺id（支持负数，高德地图店铺）',
    `user_id` bigint(20) UNSIGNED NOT NULL COMMENT '用户id',
    `score` int(2) UNSIGNED NOT NULL DEFAULT 50 COMMENT '评分，1~5分，乘10保存',
    `taste_score` int(2) UNSIGNED DEFAULT NULL COMMENT '口味评分',
    `env_score` int(2) UNSIGNED DEFAULT NULL COMMENT '环境评分',
    `service_score` int(2) UNSIGNED DEFAULT NULL COMMENT '服务评分',
    `content` varchar(2048) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '评价内容',
    `images` varchar(2048) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '评价图片，多张以","隔开',
    `liked` int(8) UNSIGNED DEFAULT 0 COMMENT '点赞数',
    `status` tinyint(1) UNSIGNED DEFAULT 0 COMMENT '状态：0正常，1隐藏',
    `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_shop_id`(`shop_id`) USING BTREE,
    INDEX `idx_user_id`(`user_id`) USING BTREE,
    INDEX `idx_create_time`(`create_time`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '店铺评价表' ROW_FORMAT = Compact;

-- 2. 店铺评价标签统计表
DROP TABLE IF EXISTS `tb_shop_comment_tag`;
CREATE TABLE `tb_shop_comment_tag` (
    `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `shop_id` bigint(20) NOT NULL COMMENT '店铺id',
    `tag_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '标签名称，如"味道赞"',
    `count` int(8) UNSIGNED DEFAULT 0 COMMENT '标签被使用次数',
    `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_shop_tag`(`shop_id`, `tag_name`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '店铺评价标签统计表' ROW_FORMAT = Compact;

-- 3. 用户店铺收藏表
DROP TABLE IF EXISTS `tb_shop_favorite`;
CREATE TABLE `tb_shop_favorite` (
    `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` bigint(20) UNSIGNED NOT NULL COMMENT '用户id',
    `shop_id` bigint(20) NOT NULL COMMENT '店铺id（支持负数）',
    `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_user_shop`(`user_id`, `shop_id`) USING BTREE,
    INDEX `idx_user_id`(`user_id`) USING BTREE,
    INDEX `idx_shop_id`(`shop_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户店铺收藏表' ROW_FORMAT = Compact;

-- 4. 店铺电话表（支持多个电话）
DROP TABLE IF EXISTS `tb_shop_phone`;
CREATE TABLE `tb_shop_phone` (
    `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `shop_id` bigint(20) NOT NULL COMMENT '店铺id（支持负数）',
    `phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '电话号码',
    `phone_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '座机' COMMENT '电话类型：座机/手机',
    `is_primary` tinyint(1) DEFAULT 0 COMMENT '是否主电话',
    `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_shop_id`(`shop_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '店铺电话表' ROW_FORMAT = Compact;

-- 5. 店铺扩展信息表（高德地图特有信息）
DROP TABLE IF EXISTS `tb_shop_extend`;
CREATE TABLE `tb_shop_extend` (
    `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `shop_id` bigint(20) NOT NULL COMMENT '店铺id（支持负数，高德地图店铺）',
    `source` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'amap' COMMENT '数据来源：amap-高德，local-本地',
    `amap_poi_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '高德POI ID',
    `business_area` varchar(128) DEFAULT NULL COMMENT '商圈',
    `indoor_map` tinyint(1) DEFAULT 0 COMMENT '是否有室内地图',
    `rating` decimal(2,1) DEFAULT NULL COMMENT '高德评分',
    `cost` decimal(10,2) DEFAULT NULL COMMENT '人均消费',
    `parking` varchar(255) DEFAULT NULL COMMENT '停车信息',
    `wifi` tinyint(1) DEFAULT 0 COMMENT '是否有WiFi',
    `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_shop_id`(`shop_id`) USING BTREE,
    INDEX `idx_amap_poi_id`(`amap_poi_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '店铺扩展信息表' ROW_FORMAT = Compact;

-- 插入一些示例评价标签
INSERT INTO `tb_shop_comment_tag` (`shop_id`, `tag_name`, `count`) VALUES
(0, '味道赞', 0),
(0, '牛肉赞', 0),
(0, '菜品不错', 0),
(0, '回头客', 0),
(0, '分量足', 0),
(0, '环境好', 0),
(0, '服务热情', 0),
(0, '上菜快', 0),
(0, '性价比高', 0),
(0, '值得回购', 0);

-- 插入一些示例评价数据（用于测试）
INSERT INTO `tb_shop_comment` (`shop_id`, `user_id`, `score`, `taste_score`, `env_score`, `service_score`, `content`, `images`, `liked`, `create_time`) VALUES
(-10001, 1, 45, 50, 45, 40, '某平台上买的券，价格可以当工作餐吃，虽然价格便宜，但是这家店一点都没有偷工减料，味道真的很不错！环境也很干净，服务态度很好，下次还会再来！', 'https://qcloud.dpfile.com/pc/6T7MfXzx7USPIkSy7jzm40qZSmlHUF2jd-FZUL6WpjE9byagjLlrseWxnl1LcbuSGybIjx5eX6WNgCPvcASYAw.jpg,https://qcloud.dpfile.com/pc/sZ5q-zgglv4VXEWU71xCFjnLM_jUHq-ylq0GKivtrz3JksWQ1f7oBWZsxm1DWgcaGybIjx5eX6WNgCPvcASYAw.jpg,https://qcloud.dpfile.com/pc/xZy6W4NwuRFchlOi43DVLPFsx7KWWvPqifE1JTe_jreqdsBYA9CFkeSm2ZlF0OVmGybIjx5eX6WNgCPvcASYAw.jpg', 12, '2024-01-15 10:30:00'),
(-10001, 2, 50, 50, 50, 50, '非常棒的体验！菜品新鲜，味道正宗，服务员态度也很好。特别推荐他们的招牌菜，真的很好吃！', NULL, 8, '2024-01-14 15:20:00'),
(-10001, 3, 40, 45, 40, 35, '整体还不错，就是等位时间有点长。菜品味道可以，环境也还行，就是服务响应慢了点。', NULL, 3, '2024-01-13 19:00:00');
