-- [字符集] 强制客户端连接使用 utf8mb4。
-- Docker 首次初始化时 mysql 客户端默认可能是 latin1, 会把 UTF-8 中文双重编码入库(页面表现为乱码)。
SET NAMES utf8mb4;

-- =====================================================================
-- v5: 客户资料完善(证件/生日/性别) + 实名认证
-- 背景:查保单、查理赔等涉及本人业务数据的问题,必须先实名认证;
--      个人资料也只支持昵称/邮箱,补齐证件类型、证件号、生日、性别。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. cus_user 补齐资料字段与实名认证状态
-- cert_type 见 CertTypeEnum:ID_CARD/PASSPORT/OFFICER/SOLDIER/HK_MACAO/TAIWAN/FOREIGN_RESIDENCE
-- cert_no 明文存储,出口一律由序列化器打码(与 ins_policy.insured_id_card 同策略)
-- real_verified:0-未认证 1-已认证;未认证客户提问本人业务数据会被引导先认证
-- ---------------------------------------------------------------------
ALTER TABLE cus_user
    ADD COLUMN cert_type VARCHAR(20) DEFAULT NULL COMMENT '证件类型:ID_CARD/PASSPORT/OFFICER/SOLDIER/HK_MACAO/TAIWAN/FOREIGN_RESIDENCE' AFTER real_name,
    ADD COLUMN cert_no VARCHAR(64) DEFAULT NULL COMMENT '证件号(出口打码)' AFTER cert_type,
    ADD COLUMN birthday DATE DEFAULT NULL COMMENT '出生日期' AFTER gender,
    ADD COLUMN real_verified TINYINT NOT NULL DEFAULT 0 COMMENT '实名认证:0-未认证 1-已认证' AFTER birthday,
    ADD COLUMN real_verified_time DATETIME DEFAULT NULL COMMENT '实名认证时间' AFTER real_verified;
