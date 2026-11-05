-- [字符集] 强制客户端连接使用 utf8mb4。
-- Docker 首次初始化时 mysql 客户端默认可能是 latin1, 会把 UTF-8 中文双重编码入库(页面表现为乱码)。
SET NAMES utf8mb4;

-- ============================================================================
-- 增量脚本 v2：模型配置 + 保单 + 理赔
--
-- 说明：
--   1. 本脚本幂等，可在已有库上重复执行
--   2. 模型配置的 API Key 不在脚本中写明文，由应用启动时从 application.yml
--      读取并 AES-256-GCM 加密后写入（见 AiModelInitializer）
--   3. 保单/理赔演示数据覆盖两个客户，用于验证数据隔离（客户只能查自己的）
-- ============================================================================

USE `insurance_ai`;

-- ----------------------------------------------------------------------------
-- 1. 大模型配置：支持多厂商、多套配置，API Key 密文存储
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ai_model_config`
(
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `config_name`    VARCHAR(64)  NOT NULL COMMENT '配置名称,如 通义千问-生产',
    `provider`       VARCHAR(32)  NOT NULL DEFAULT 'OPENAI_COMPATIBLE' COMMENT '提供方: DASHSCOPE-阿里百炼 OPENAI_COMPATIBLE-OpenAI兼容(DeepSeek/vLLM/Ollama/内网网关)',
    `base_url`       VARCHAR(255)          DEFAULT NULL COMMENT '接口地址,留空则用厂商默认;内网部署时指向行内模型网关',
    `api_key_cipher` VARCHAR(1024)         DEFAULT NULL COMMENT 'API Key 密文(AES-256-GCM,Base64)',
    `api_key_mask`   VARCHAR(64)           DEFAULT NULL COMMENT 'API Key 掩码,仅用于界面展示,如 sk-****3f2a',
    `model`          VARCHAR(64)  NOT NULL COMMENT '模型名称,如 qwen-plus / deepseek-chat',
    `temperature`    DECIMAL(3, 2) NOT NULL DEFAULT 0.30 COMMENT '采样温度,保险场景建议 0.1~0.4',
    `max_tokens`     INT          NOT NULL DEFAULT 1024 COMMENT '单次回复最大 token 数',
    `timeout_ms`     INT          NOT NULL DEFAULT 60000 COMMENT '读超时(毫秒)',
    `is_default`     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否默认配置: 1-是 0-否',
    `status`         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 1-启用 0-停用',
    `remark`         VARCHAR(500)          DEFAULT NULL COMMENT '备注',
    `create_by`      VARCHAR(64)           DEFAULT NULL COMMENT '创建者',
    `create_time`    DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`      VARCHAR(64)           DEFAULT NULL COMMENT '修改者',
    `update_time`    DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `deleted`        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_ai_model_status` (`status`),
    KEY `idx_ai_model_default` (`is_default`)
) ENGINE = InnoDB COMMENT ='大模型配置表';

-- ----------------------------------------------------------------------------
-- 2. 保单
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ins_policy`
(
    `id`              BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `policy_no`       VARCHAR(40)    NOT NULL COMMENT '保单号',
    `cus_user_id`     BIGINT         NOT NULL COMMENT '所属客户ID(数据隔离依据)',
    `product_name`    VARCHAR(100)   NOT NULL COMMENT '产品名称',
    `product_code`    VARCHAR(40)             DEFAULT NULL COMMENT '产品代码',
    `category`        VARCHAR(20)    NOT NULL DEFAULT 'OTHER' COMMENT '险种: CRITICAL-重疾 MEDICAL-医疗 ACCIDENT-意外 LIFE-寿险 ANNUITY-年金 OTHER-其他',
    `holder_name`     VARCHAR(50)             DEFAULT NULL COMMENT '投保人姓名',
    `insured_name`    VARCHAR(50)             DEFAULT NULL COMMENT '被保人姓名',
    `insured_id_card` VARCHAR(40)             DEFAULT NULL COMMENT '被保人证件号(脱敏存储)',
    `premium`         DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT '年缴保费(元)',
    `coverage_amount` DECIMAL(14, 2) NOT NULL DEFAULT 0.00 COMMENT '保额(元)',
    `pay_years`       INT                     DEFAULT NULL COMMENT '缴费年限,空表示趸交',
    `effective_date`  DATE           NOT NULL COMMENT '生效日期',
    `expire_date`     DATE           NOT NULL COMMENT '保险期间终止日期',
    `next_pay_date`   DATE                    DEFAULT NULL COMMENT '下次缴费日期',
    `status`          TINYINT        NOT NULL DEFAULT 1 COMMENT '状态: 1-有效 2-中止 3-失效 4-已退保 5-满期终止',
    `remark`          VARCHAR(500)            DEFAULT NULL COMMENT '备注',
    `create_by`       VARCHAR(64)             DEFAULT NULL COMMENT '创建者',
    `create_time`     DATETIME                DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       VARCHAR(64)             DEFAULT NULL COMMENT '修改者',
    `update_time`     DATETIME                DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `deleted`         TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_policy_no` (`policy_no`, `deleted`),
    KEY `idx_policy_cus` (`cus_user_id`),
    KEY `idx_policy_status` (`status`)
) ENGINE = InnoDB COMMENT ='保单表';

-- ----------------------------------------------------------------------------
-- 3. 理赔
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ins_claim`
(
    `id`                BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `claim_no`          VARCHAR(40)    NOT NULL COMMENT '理赔案号',
    `cus_user_id`       BIGINT         NOT NULL COMMENT '所属客户ID(数据隔离依据)',
    `policy_id`         BIGINT         NOT NULL COMMENT '关联保单ID',
    `policy_no`         VARCHAR(40)    NOT NULL COMMENT '关联保单号(冗余,便于查询)',
    `claim_type`        VARCHAR(20)    NOT NULL DEFAULT 'MEDICAL' COMMENT '理赔类型: MEDICAL-医疗 CRITICAL-重疾 ACCIDENT-意外 DEATH-身故',
    `accident_date`     DATE                    DEFAULT NULL COMMENT '出险日期',
    `report_date`       DATE           NOT NULL COMMENT '报案日期',
    `claim_amount`      DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT '申请赔付金额(元)',
    `approved_amount`   DECIMAL(12, 2)          DEFAULT NULL COMMENT '核定赔付金额(元),未结案为空',
    `status`            TINYINT        NOT NULL DEFAULT 1 COMMENT '状态: 1-已报案 2-资料审核 3-调查中 4-审核通过 5-已赔付 6-拒赔 7-已撤销',
    `current_node`      VARCHAR(100)            DEFAULT NULL COMMENT '当前所处环节描述',
    `handler`           VARCHAR(50)             DEFAULT NULL COMMENT '当前处理人',
    `expected_pay_date` DATE                    DEFAULT NULL COMMENT '预计赔付日期',
    `close_date`        DATE                    DEFAULT NULL COMMENT '结案日期',
    `reject_reason`     VARCHAR(500)            DEFAULT NULL COMMENT '拒赔原因',
    `remark`            VARCHAR(500)            DEFAULT NULL COMMENT '备注',
    `create_by`         VARCHAR(64)             DEFAULT NULL COMMENT '创建者',
    `create_time`       DATETIME                DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`         VARCHAR(64)             DEFAULT NULL COMMENT '修改者',
    `update_time`       DATETIME                DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `deleted`           TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_claim_no` (`claim_no`, `deleted`),
    KEY `idx_claim_cus` (`cus_user_id`),
    KEY `idx_claim_policy` (`policy_id`),
    KEY `idx_claim_status` (`status`)
) ENGINE = InnoDB COMMENT ='理赔案件表';

-- ----------------------------------------------------------------------------
-- 4. 理赔进度（时间轴）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ins_claim_progress`
(
    `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `claim_id`      BIGINT      NOT NULL COMMENT '理赔案件ID',
    `node`          VARCHAR(50) NOT NULL COMMENT '环节名称,如 资料审核',
    `node_desc`     VARCHAR(500)         DEFAULT NULL COMMENT '环节说明',
    `operator_name` VARCHAR(50)          DEFAULT NULL COMMENT '处理人',
    `progress_time` DATETIME    NOT NULL COMMENT '发生时间',
    `finished`      TINYINT     NOT NULL DEFAULT 1 COMMENT '是否已完成: 1-已完成 0-进行中',
    `remark`        VARCHAR(500)         DEFAULT NULL COMMENT '备注',
    `create_by`     VARCHAR(64)          DEFAULT NULL COMMENT '创建者',
    `create_time`   DATETIME             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `deleted`       TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_progress_claim` (`claim_id`)
) ENGINE = InnoDB COMMENT ='理赔进度表';

-- ----------------------------------------------------------------------------
-- 5. 菜单：保单理赔（客户服务下）+ 模型配置（系统管理下）
-- ----------------------------------------------------------------------------
INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `icon`, `perms`,
                               `sort`, `visible`, `status`, `create_by`)
VALUES (12, 10, '保单理赔', 'MENU', '/service/policy', 'admin/policy/index', 'Tickets', 'policy:list', 2, 1, 1,
        'system'),
       (34, 30, '模型配置', 'MENU', '/system/model', 'admin/model/index', 'Cpu', 'sys:model:list', 4, 1, 1,
        'system');

-- 超级管理员(role_id=1)授权；客服专员(role_id=2)仅授保单理赔
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`, `create_by`)
VALUES (1, 12, 'system'),
       (1, 34, 'system'),
       (2, 12, 'system');

-- ----------------------------------------------------------------------------
-- 6. 演示数据：第二个客户（用于验证数据隔离）
-- ----------------------------------------------------------------------------
INSERT IGNORE INTO `cus_user` (`id`, `username`, `password`, `nickname`, `real_name`, `phone`, `email`, `gender`,
                               `status`, `register_source`, `remark`, `create_by`)
VALUES (2, 'customer02', '$2a$10$0WDE7TP9Voa.ZJX6vaufEuAojK5HD0YQYECOhJI6KABeycNCptKUi', '小李', '李四',
        '13900000002', 'customer02@example.com', 2, 1, 'H5', '演示用第二客户,用于验证保单数据隔离', 'system');

-- 保单：客户1 两张，客户2 一张
INSERT IGNORE INTO `ins_policy` (`id`, `policy_no`, `cus_user_id`, `product_name`, `product_code`, `category`,
                                 `holder_name`, `insured_name`, `insured_id_card`, `premium`, `coverage_amount`,
                                 `pay_years`, `effective_date`, `expire_date`, `next_pay_date`, `status`, `create_by`)
VALUES (1, 'P202306150001', 1, '守护一生终身重大疾病保险', 'CI2023001', 'CRITICAL', '张三', '张三', '3301**********1234',
        8600.00, 500000.00, 20, '2023-06-15', '2099-12-31', '2026-06-15', 1, 'system'),
       (2, 'P202403010002', 1, '安享百万医疗保险(2024版)', 'MD2024002', 'MEDICAL', '张三', '张三', '3301**********1234',
        680.00, 3000000.00, NULL, '2024-03-01', '2027-02-28', '2026-03-01', 1, 'system'),
       (3, 'P202501100003', 2, '畅行无忧综合意外险', 'AC2025003', 'ACCIDENT', '李四', '李四', '3302**********5678',
        300.00, 500000.00, NULL, '2025-01-10', '2027-01-09', '2026-01-10', 1, 'system');

-- 理赔：客户1 两条（一条已赔付、一条调查中），客户2 一条（用于越权测试）
INSERT IGNORE INTO `ins_claim` (`id`, `claim_no`, `cus_user_id`, `policy_id`, `policy_no`, `claim_type`,
                                `accident_date`, `report_date`, `claim_amount`, `approved_amount`, `status`,
                                `current_node`, `handler`, `expected_pay_date`, `close_date`, `create_by`)
VALUES (1, 'CLM20261020001', 1, 2, 'P202403010002', 'MEDICAL', '2026-10-18', '2026-10-20', 12800.00, 11200.00, 5,
        '已结案', '王审核', '2026-10-28', '2026-10-27', 'system'),
       (2, 'CLM20261101002', 1, 2, 'P202403010002', 'MEDICAL', '2026-10-30', '2026-11-01', 5600.00, NULL, 3,
        '调查中', '李调查', '2026-11-15', NULL, 'system'),
       (3, 'CLM20261025003', 2, 3, 'P202501100003', 'ACCIDENT', '2026-10-23', '2026-10-25', 3200.00, NULL, 2,
        '资料审核', '赵审核', '2026-11-10', NULL, 'system');

INSERT IGNORE INTO `ins_claim_progress` (`id`, `claim_id`, `node`, `node_desc`, `operator_name`, `progress_time`,
                                         `finished`, `create_by`)
VALUES (1, 1, '提交报案', '客户通过手机端提交理赔申请', '张三', '2026-10-20 09:12:00', 1, 'system'),
       (2, 1, '资料审核', '门诊病历、发票、费用清单齐全', '王审核', '2026-10-21 14:30:00', 1, 'system'),
       (3, 1, '审核通过', '核定赔付 11200.00 元', '王审核', '2026-10-27 10:05:00', 1, 'system'),
       (4, 1, '赔款支付', '已转账至客户预留银行账户', '财务', '2026-10-27 16:40:00', 1, 'system'),
       (5, 2, '提交报案', '客户通过手机端提交理赔申请', '张三', '2026-11-01 08:20:00', 1, 'system'),
       (6, 2, '资料审核', '资料已收齐,转入理赔调查', '李调查', '2026-11-02 11:15:00', 1, 'system'),
       (7, 2, '调查中', '正在核实既往就诊记录', '李调查', '2026-11-03 09:00:00', 0, 'system'),
       (8, 3, '提交报案', '客户通过手机端提交理赔申请', '李四', '2026-10-25 15:40:00', 1, 'system'),
       (9, 3, '资料审核', '等待补充事故证明', '赵审核', '2026-10-26 10:20:00', 0, 'system');
