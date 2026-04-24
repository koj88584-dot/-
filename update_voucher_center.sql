ALTER TABLE tb_voucher
  ADD COLUMN stock INT UNSIGNED NOT NULL DEFAULT 100 COMMENT '库存' AFTER status,
  ADD COLUMN begin_time TIMESTAMP NULL DEFAULT NULL COMMENT '生效时间' AFTER stock,
  ADD COLUMN end_time TIMESTAMP NULL DEFAULT NULL COMMENT '失效时间' AFTER begin_time;

UPDATE tb_voucher
SET stock = 100
WHERE stock IS NULL OR stock <= 0;

ALTER TABLE tb_voucher_order
  ADD COLUMN verify_code VARCHAR(32) NULL COMMENT '券码' AFTER refund_time,
  ADD COLUMN effective_time TIMESTAMP NULL DEFAULT NULL COMMENT '生效时间' AFTER verify_code,
  ADD COLUMN expire_time TIMESTAMP NULL DEFAULT NULL COMMENT '失效时间' AFTER effective_time,
  ADD UNIQUE INDEX uk_verify_code (verify_code);
