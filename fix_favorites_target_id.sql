-- ============================================
-- 修复收藏夹表 target_id 字段类型
-- 支持负数店铺ID（高德地图店铺）
-- ============================================

-- 修改 tb_favorites 表的 target_id 字段，支持负数
ALTER TABLE `tb_favorites` 
MODIFY COLUMN `target_id` bigint(20) NOT NULL COMMENT '收藏的目标id（店铺id或博客id，支持负数）';

-- 修改 tb_browse_history 表的 target_id 字段，支持负数（保持一致性）
ALTER TABLE `tb_browse_history` 
MODIFY COLUMN `target_id` bigint(20) NOT NULL COMMENT '浏览的目标id（店铺id或博客id，支持负数）';

-- 查看修改后的表结构
DESCRIBE `tb_favorites`;
DESCRIBE `tb_browse_history`;
