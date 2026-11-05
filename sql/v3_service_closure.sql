-- [字符集] 强制客户端连接使用 utf8mb4。
-- Docker 首次初始化时 mysql 客户端默认可能是 latin1, 会把 UTF-8 中文双重编码入库(页面表现为乱码)。
SET NAMES utf8mb4;

-- =====================================================================
-- v3 服务闭环：回答评价 / 转人工工单 / 知识盲区沉淀
-- 适用：MySQL 8.0，字符集 utf8mb4
-- 执行方式：mysql -uroot -p insurance_ai < sql/v3_service_closure.sql
-- =====================================================================

USE `insurance_ai`;

-- ---------------------------------------------------------------------
-- 1. 回答评价
--    客户对某条 AI 回复做「有用 / 没用」评价，差评需带原因，用于定位模型与知识库问题
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `chat_feedback`
(
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `message_id`  BIGINT      NOT NULL COMMENT '被评价的 AI 消息ID(chat_message.id)',
    `session_id`  BIGINT      NOT NULL COMMENT '会话ID',
    `cus_user_id` BIGINT      NOT NULL COMMENT '评价客户ID',
    `rating`      TINYINT     NOT NULL COMMENT '评价: 1-有用 0-没用',
    `reason`      VARCHAR(30)          DEFAULT NULL COMMENT '差评原因: WRONG-信息有误 IRRELEVANT-答非所问 INCOMPLETE-不够完整 SLOW-响应太慢 OTHER-其他',
    `content`     VARCHAR(500)         DEFAULT NULL COMMENT '补充说明',
    `create_by`   VARCHAR(64)          DEFAULT NULL COMMENT '创建者',
    `create_time` DATETIME             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   VARCHAR(64)          DEFAULT NULL COMMENT '修改者',
    `update_time` DATETIME             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `deleted`     TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    `remark`      VARCHAR(500)         DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    -- 同一条消息同一客户只能评价一次（重复提交做覆盖更新，不新增行）
    UNIQUE KEY `uk_feedback_message_user` (`message_id`, `cus_user_id`),
    KEY `idx_feedback_session` (`session_id`),
    KEY `idx_feedback_rating` (`rating`, `create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='AI 回答评价';

-- ---------------------------------------------------------------------
-- 2. 人工工单
--    AI 无法解决时客户一键转人工，坐席在后台接单处理，形成服务兜底闭环
--    表名用 svc_ticket 而非 service_ticket：service 在部分数据库中是保留词，避免转义麻烦
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `svc_ticket`
(
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `ticket_no`    VARCHAR(32)  NOT NULL COMMENT '工单号,如 TK202611040001',
    `cus_user_id`  BIGINT       NOT NULL COMMENT '提单客户ID',
    `session_id`   BIGINT                DEFAULT NULL COMMENT '关联会话ID',
    `question`     VARCHAR(500) NOT NULL COMMENT '客户问题摘要(AI 已无法解决的内容)',
    `category`     VARCHAR(30)           DEFAULT 'OTHER' COMMENT '业务分类,同 kb_question.category',
    `reason`       VARCHAR(30)           DEFAULT 'OTHER' COMMENT '转人工原因: DISSATISFIED-回答不满意 COMPLAINT-投诉建议 COMPLEX-业务复杂 OTHER-其他',
    `status`       TINYINT      NOT NULL DEFAULT 0 COMMENT '状态: 0-待处理 1-处理中 2-已完成 3-已取消',
    `priority`     TINYINT      NOT NULL DEFAULT 1 COMMENT '优先级: 1-普通 2-紧急',
    `handler_id`   BIGINT                DEFAULT NULL COMMENT '处理人ID(sys_user.id)',
    `handler_name` VARCHAR(50)           DEFAULT NULL COMMENT '处理人姓名',
    `reply`        VARCHAR(1000)         DEFAULT NULL COMMENT '处理回复(坐席填写,客户可见)',
    `accept_time`  DATETIME              DEFAULT NULL COMMENT '接单时间',
    `finish_time`  DATETIME              DEFAULT NULL COMMENT '完成时间',
    `create_by`    VARCHAR(64)           DEFAULT NULL COMMENT '创建者',
    `create_time`  DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`    VARCHAR(64)           DEFAULT NULL COMMENT '修改者',
    `update_time`  DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `deleted`      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    `remark`       VARCHAR(500)          DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ticket_no` (`ticket_no`),
    KEY `idx_ticket_cus_user` (`cus_user_id`, `create_time`),
    KEY `idx_ticket_status` (`status`, `create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='人工客服工单';

-- ---------------------------------------------------------------------
-- 3. 知识盲区
--    知识库召回为空或仅弱命中时落一条记录，后台按问题聚合排序，
--    运营可一键转成知识库草稿，让知识库随着真实提问自己长出来
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `chat_unknown`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `cus_user_id` BIGINT                DEFAULT NULL COMMENT '提问客户ID',
    `session_id`  BIGINT                DEFAULT NULL COMMENT '会话ID',
    `question`    VARCHAR(500) NOT NULL COMMENT '未命中问题原文',
    `best_score`  INT          NOT NULL DEFAULT 0 COMMENT '知识库最高召回得分,0 表示完全没命中',
    `status`      TINYINT      NOT NULL DEFAULT 0 COMMENT '状态: 0-待处理 1-已转知识 2-已忽略',
    `kb_id`       BIGINT                DEFAULT NULL COMMENT '转化后的知识条目ID',
    `create_by`   VARCHAR(64)           DEFAULT NULL COMMENT '创建者',
    `create_time` DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   VARCHAR(64)           DEFAULT NULL COMMENT '修改者',
    `update_time` DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    `remark`      VARCHAR(500)          DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    KEY `idx_unknown_status` (`status`, `create_time`),
    KEY `idx_unknown_question` (`question`(100))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='知识库未命中问题(知识盲区)';

-- ---------------------------------------------------------------------
-- 4. 菜单：客户服务下增加「服务工单」，知识库下增加「知识缺口」
-- 注意：12 已被 v2 的「保单理赔」占用，这里工单用 13，避免主键冲突把保单菜单改名
-- ---------------------------------------------------------------------
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `icon`, `perms`, `sort`,
                        `visible`, `status`, `create_by`)
VALUES (13, 10, '服务工单', 'MENU', '/service/ticket', 'admin/ticket/index', 'Tickets', 'ticket:list', 3, 1, 1, 'system'),
       (21, 0, '知识缺口', 'MENU', '/kb/gap', 'admin/gap/index', 'Warning', 'kb:gap:list', 4, 1, 1, 'system')
ON DUPLICATE KEY UPDATE `parent_id` = VALUES(`parent_id`),
                        `menu_name` = VALUES(`menu_name`),
                        `path`      = VALUES(`path`),
                        `component` = VALUES(`component`),
                        `icon`      = VALUES(`icon`),
                        `perms`     = VALUES(`perms`),
                        `sort`      = VALUES(`sort`),
                        `visible`   = VALUES(`visible`),
                        `status`    = VALUES(`status`);

-- 修复被旧版脚本改名/改路由的「保单理赔」菜单
UPDATE `sys_menu`
SET `menu_name` = '保单理赔',
    `path`      = '/service/policy',
    `component` = 'admin/policy/index',
    `icon`      = 'Tickets',
    `perms`     = 'policy:list'
WHERE `id` = 12;

-- 超管全部菜单；客服专员可处理工单、可看知识缺口
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`, `create_by`)
VALUES (1, 13, 'system'), (1, 21, 'system'),
       (2, 13, 'system'), (2, 21, 'system')
ON DUPLICATE KEY UPDATE `create_by` = VALUES(`create_by`);
