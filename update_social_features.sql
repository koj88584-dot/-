-- HMDP social feature migration: chat, profile visits, and privacy flags.
-- Run this after the base schema has been imported.

CREATE TABLE IF NOT EXISTS `tb_chat_conversation` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id_a` bigint(20) UNSIGNED NOT NULL COMMENT '用户A（ID较小的一方）',
  `user_id_b` bigint(20) UNSIGNED NOT NULL COMMENT '用户B（ID较大的一方）',
  `last_message` varchar(500) DEFAULT NULL COMMENT '最新消息内容',
  `last_sender_id` bigint(20) UNSIGNED DEFAULT NULL COMMENT '最新消息发送者',
  `unread_count_a` int UNSIGNED DEFAULT 0 COMMENT 'A的未读数',
  `unread_count_b` int UNSIGNED DEFAULT 0 COMMENT 'B的未读数',
  `last_message_time` timestamp NULL DEFAULT NULL COMMENT '最新消息时间',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users` (`user_id_a`, `user_id_b`),
  KEY `idx_user_a` (`user_id_a`),
  KEY `idx_user_b` (`user_id_b`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='聊天会话表';

CREATE TABLE IF NOT EXISTS `tb_chat_message` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `conversation_id` bigint(20) UNSIGNED NOT NULL COMMENT '会话ID',
  `sender_id` bigint(20) UNSIGNED NOT NULL COMMENT '发送者ID',
  `receiver_id` bigint(20) UNSIGNED NOT NULL COMMENT '接收者ID',
  `content` varchar(2000) NOT NULL COMMENT '消息内容',
  `type` tinyint UNSIGNED DEFAULT 1 COMMENT '1-文本 2-图片',
  `is_read` tinyint UNSIGNED DEFAULT 0 COMMENT '是否已读',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_conversation` (`conversation_id`),
  KEY `idx_sender_receiver` (`sender_id`, `receiver_id`),
  KEY `idx_receiver` (`receiver_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='聊天消息表';

CREATE TABLE IF NOT EXISTS `tb_profile_visit` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `visitor_id` bigint(20) UNSIGNED NOT NULL COMMENT '访问者ID',
  `visited_id` bigint(20) UNSIGNED NOT NULL COMMENT '被访问者ID',
  `visit_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '访问时间',
  `is_stealth` tinyint UNSIGNED DEFAULT 0 COMMENT '是否隐身访问',
  `is_read` tinyint UNSIGNED DEFAULT 0 COMMENT '是否已读',
  PRIMARY KEY (`id`),
  KEY `idx_visited_time` (`visited_id`, `visit_time`),
  KEY `idx_visitor` (`visitor_id`),
  KEY `idx_unread` (`visited_id`, `is_read`, `is_stealth`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='主页访客记录表';

SET @schema_name = DATABASE();

SET @has_stealth_mode = (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name
    AND TABLE_NAME = 'tb_privacy_setting'
    AND COLUMN_NAME = 'stealth_mode'
);
SET @add_stealth_mode = IF(
  @has_stealth_mode = 0,
  'ALTER TABLE `tb_privacy_setting` ADD COLUMN `stealth_mode` tinyint(1) UNSIGNED NOT NULL DEFAULT 0 COMMENT ''隐身访问：0-关闭 1-开启''',
  'SELECT ''stealth_mode already exists'''
);
PREPARE stmt FROM @add_stealth_mode;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_allow_visit_notify = (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name
    AND TABLE_NAME = 'tb_privacy_setting'
    AND COLUMN_NAME = 'allow_visit_notify'
);
SET @add_allow_visit_notify = IF(
  @has_allow_visit_notify = 0,
  'ALTER TABLE `tb_privacy_setting` ADD COLUMN `allow_visit_notify` tinyint(1) UNSIGNED NOT NULL DEFAULT 1 COMMENT ''允许访客通知：0-关闭 1-允许''',
  'SELECT ''allow_visit_notify already exists'''
);
PREPARE stmt FROM @add_allow_visit_notify;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
