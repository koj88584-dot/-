-- City-driven nationalization support.
-- Run this once before using cityCode filters in the shop/search/map APIs.

ALTER TABLE `tb_shop`
  ADD COLUMN `city_code` varchar(32) DEFAULT NULL COMMENT 'city code' AFTER `area`,
  ADD COLUMN `province` varchar(64) DEFAULT NULL COMMENT 'province' AFTER `city_code`,
  ADD COLUMN `city` varchar(64) DEFAULT NULL COMMENT 'city' AFTER `province`,
  ADD COLUMN `district` varchar(64) DEFAULT NULL COMMENT 'district' AFTER `city`,
  ADD COLUMN `adcode` varchar(32) DEFAULT NULL COMMENT 'amap adcode' AFTER `district`;

CREATE INDEX `idx_shop_city_type` ON `tb_shop` (`city_code`, `type_id`);
CREATE INDEX `idx_shop_adcode_type` ON `tb_shop` (`adcode`, `type_id`);

-- Existing demo data is Hangzhou-local. Keep it available as the first city sample room.
UPDATE `tb_shop`
SET
  `city_code` = COALESCE(`city_code`, '330100'),
  `province` = COALESCE(`province`, '浙江'),
  `city` = COALESCE(`city`, '杭州'),
  `district` = COALESCE(`district`, '拱墅区'),
  `adcode` = COALESCE(`adcode`, '330105')
WHERE `id` BETWEEN 1 AND 14;

-- Optional smoke checks:
-- SELECT city_code, city, COUNT(*) FROM tb_shop GROUP BY city_code, city;
-- SELECT id, name, city_code, province, city, district, adcode FROM tb_shop LIMIT 20;
