-- =====================================================================
-- 保险智能客服系统 数据库初始化脚本（双用户体系版）
-- 数据库: MySQL 8.0+
--
-- 【双用户体系说明】
--   sys_ 前缀  -> 管理端（业务操作人员 / 客服坐席 / 管理员），登录入口 /admin/auth/login
--   cus_ 前缀  -> 客户端（投保咨询客户），登录入口 /auth/login
--   两套账号数据完全隔离，token 载荷中通过 userType 区分，管理端接口只允许 ADMIN 访问
--
-- 【公共字段约定】
--   所有表均包含：id(bigint 自增)、create_by、create_time、update_by、update_time、deleted、remark
-- =====================================================================

CREATE DATABASE IF NOT EXISTS `insurance_ai`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

USE `insurance_ai`;

DROP TABLE IF EXISTS `sys_role_menu`;
DROP TABLE IF EXISTS `sys_menu`;
DROP TABLE IF EXISTS `sys_role`;
DROP TABLE IF EXISTS `sys_login_log`;
DROP TABLE IF EXISTS `sys_oper_log`;
DROP TABLE IF EXISTS `sys_file`;
DROP TABLE IF EXISTS `sys_user`;
DROP TABLE IF EXISTS `chat_message`;
DROP TABLE IF EXISTS `chat_session`;
DROP TABLE IF EXISTS `kb_question`;
DROP TABLE IF EXISTS `cus_user`;

-- ---------------------------------------------------------------------
-- 1. 管理端用户表（业务操作人员）
-- ---------------------------------------------------------------------
CREATE TABLE `sys_user`
(
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `username`        VARCHAR(50)  NOT NULL COMMENT '登录账号',
    `password`        VARCHAR(100) NOT NULL COMMENT '登录密码(BCrypt 加密)',
    `real_name`       VARCHAR(50)           DEFAULT NULL COMMENT '真实姓名',
    `phone`           VARCHAR(20)  NOT NULL COMMENT '手机号(管理端内唯一)',
    `email`           VARCHAR(100)          DEFAULT NULL COMMENT '电子邮箱',
    `avatar`          VARCHAR(255)          DEFAULT NULL COMMENT '头像地址',
    `role_id`         BIGINT       NOT NULL DEFAULT 2 COMMENT '角色ID(关联 sys_role.id)',
    `status`          TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 1-启用 0-禁用',
    `last_login_time` DATETIME              DEFAULT NULL COMMENT '最后登录时间',
    `remark`          VARCHAR(500)          DEFAULT NULL COMMENT '备注',
    `create_by`       VARCHAR(64)           DEFAULT NULL COMMENT '创建者',
    `create_time`     DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       VARCHAR(64)           DEFAULT NULL COMMENT '修改者',
    `update_time`     DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sys_user_username` (`username`, `deleted`),
    UNIQUE KEY `uk_sys_user_phone` (`phone`, `deleted`),
    KEY `idx_sys_user_role` (`role_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='管理端用户表(业务操作人员)';

-- ---------------------------------------------------------------------
-- 2. 角色表
-- ---------------------------------------------------------------------
CREATE TABLE `sys_role`
(
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `role_code`   VARCHAR(50) NOT NULL COMMENT '角色标识(SUPER_ADMIN/OPERATOR)',
    `role_name`   VARCHAR(50) NOT NULL COMMENT '角色名称',
    `sort`        INT         NOT NULL DEFAULT 0 COMMENT '排序(升序)',
    `status`      TINYINT     NOT NULL DEFAULT 1 COMMENT '状态: 1-启用 0-禁用',
    `remark`      VARCHAR(500)         DEFAULT NULL COMMENT '备注',
    `create_by`   VARCHAR(64)          DEFAULT NULL COMMENT '创建者',
    `create_time` DATETIME             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   VARCHAR(64)          DEFAULT NULL COMMENT '修改者',
    `update_time` DATETIME             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `deleted`     TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sys_role_code` (`role_code`, `deleted`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='管理端角色表';

-- ---------------------------------------------------------------------
-- 3. 菜单表（后台导航，支持目录/菜单/按钮三级）
-- ---------------------------------------------------------------------
CREATE TABLE `sys_menu`
(
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `parent_id`   BIGINT      NOT NULL DEFAULT 0 COMMENT '父级菜单ID(0 表示顶级)',
    `menu_name`   VARCHAR(50) NOT NULL COMMENT '菜单名称',
    `menu_type`   VARCHAR(10) NOT NULL DEFAULT 'MENU' COMMENT '菜单类型: DIR-目录 MENU-菜单 BUTTON-按钮',
    `path`        VARCHAR(200)         DEFAULT NULL COMMENT '路由地址',
    `component`   VARCHAR(200)         DEFAULT NULL COMMENT '前端组件路径(相对 src/views)',
    `icon`        VARCHAR(64)          DEFAULT NULL COMMENT '菜单图标(Element Plus 图标名)',
    `perms`       VARCHAR(100)         DEFAULT NULL COMMENT '权限标识(如 sys:user:list)',
    `sort`        INT         NOT NULL DEFAULT 0 COMMENT '排序(升序)',
    `visible`     TINYINT     NOT NULL DEFAULT 1 COMMENT '是否显示: 1-显示 0-隐藏',
    `status`      TINYINT     NOT NULL DEFAULT 1 COMMENT '状态: 1-启用 0-禁用',
    `remark`      VARCHAR(500)         DEFAULT NULL COMMENT '备注',
    `create_by`   VARCHAR(64)          DEFAULT NULL COMMENT '创建者',
    `create_time` DATETIME             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   VARCHAR(64)          DEFAULT NULL COMMENT '修改者',
    `update_time` DATETIME             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `deleted`     TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_sys_menu_parent` (`parent_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='管理端菜单表';

-- ---------------------------------------------------------------------
-- 4. 角色-菜单关联表
-- ---------------------------------------------------------------------
CREATE TABLE `sys_role_menu`
(
    `id`          BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `role_id`     BIGINT NOT NULL COMMENT '角色ID',
    `menu_id`     BIGINT NOT NULL COMMENT '菜单ID',
    `remark`      VARCHAR(500) DEFAULT NULL COMMENT '备注',
    `create_by`   VARCHAR(64)  DEFAULT NULL COMMENT '创建者',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   VARCHAR(64)  DEFAULT NULL COMMENT '修改者',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `deleted`     TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_menu` (`role_id`, `menu_id`, `deleted`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='角色菜单关联表';

-- ---------------------------------------------------------------------
-- 5. 客户端用户表（咨询客户）
-- ---------------------------------------------------------------------
CREATE TABLE `cus_user`
(
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `username`        VARCHAR(50)           DEFAULT NULL COMMENT '登录账号(可为空,默认用手机号登录)',
    `password`        VARCHAR(100) NOT NULL COMMENT '登录密码(BCrypt 加密)',
    `nickname`        VARCHAR(50)           DEFAULT NULL COMMENT '昵称',
    `real_name`       VARCHAR(50)           DEFAULT NULL COMMENT '真实姓名',
    `cert_type`       VARCHAR(20)           DEFAULT NULL COMMENT '证件类型:ID_CARD/PASSPORT/OFFICER/SOLDIER/HK_MACAO/TAIWAN/FOREIGN_RESIDENCE',
    `cert_no`         VARCHAR(64)           DEFAULT NULL COMMENT '证件号(出口打码)',
    `phone`           VARCHAR(20)  NOT NULL COMMENT '手机号(全局唯一,用于登录与找回)',
    `email`           VARCHAR(100)          DEFAULT NULL COMMENT '电子邮箱',
    `avatar`          VARCHAR(255)          DEFAULT NULL COMMENT '头像地址',
    `gender`          TINYINT      NOT NULL DEFAULT 0 COMMENT '性别: 0-未知 1-男 2-女',
    `birthday`        DATE                  DEFAULT NULL COMMENT '出生日期',
    `real_verified`   TINYINT      NOT NULL DEFAULT 0 COMMENT '实名认证: 0-未认证 1-已认证',
    `real_verified_time` DATETIME          DEFAULT NULL COMMENT '实名认证时间',
    `status`          TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 1-启用 0-禁用',
    `register_source` VARCHAR(20)  NOT NULL DEFAULT 'H5' COMMENT '注册来源: H5-手机端',
    `last_login_time` DATETIME              DEFAULT NULL COMMENT '最后登录时间',
    `remark`          VARCHAR(500)          DEFAULT NULL COMMENT '备注',
    `create_by`       VARCHAR(64)           DEFAULT NULL COMMENT '创建者',
    `create_time`     DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       VARCHAR(64)           DEFAULT NULL COMMENT '修改者',
    `update_time`     DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cus_user_phone` (`phone`, `deleted`),
    UNIQUE KEY `uk_cus_user_username` (`username`, `deleted`),
    KEY `idx_cus_user_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='客户端用户表(咨询客户)';

-- ---------------------------------------------------------------------
-- 6. 登录日志表（区分登录端）
-- ---------------------------------------------------------------------
CREATE TABLE `sys_login_log`
(
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`     BIGINT               DEFAULT NULL COMMENT '登录用户ID(sys_user.id 或 cus_user.id)',
    `username`    VARCHAR(50) NOT NULL COMMENT '登录账号',
    `login_type`  VARCHAR(20) NOT NULL DEFAULT 'ADMIN' COMMENT '登录端: ADMIN-管理端 CUSTOMER-客户端',
    `ip`          VARCHAR(64)          DEFAULT NULL COMMENT '登录IP',
    `browser`     VARCHAR(100)         DEFAULT NULL COMMENT '浏览器类型',
    `os`          VARCHAR(100)         DEFAULT NULL COMMENT '操作系统',
    `status`      TINYINT     NOT NULL DEFAULT 1 COMMENT '登录状态: 1-成功 0-失败',
    `msg`         VARCHAR(255)         DEFAULT NULL COMMENT '提示信息',
    `login_time`  DATETIME             DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
    `remark`      VARCHAR(500)         DEFAULT NULL COMMENT '备注',
    `create_by`   VARCHAR(64)          DEFAULT NULL COMMENT '创建者',
    `create_time` DATETIME             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   VARCHAR(64)          DEFAULT NULL COMMENT '修改者',
    `update_time` DATETIME             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `deleted`     TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_login_username` (`username`),
    KEY `idx_login_type` (`login_type`),
    KEY `idx_login_time` (`login_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='登录日志表';

-- ---------------------------------------------------------------------
-- 7. 操作日志表
-- ---------------------------------------------------------------------
CREATE TABLE `sys_oper_log`
(
    `id`             BIGINT  NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `module`         VARCHAR(64)      DEFAULT NULL COMMENT '所属模块',
    `operation`      VARCHAR(64)      DEFAULT NULL COMMENT '操作描述',
    `method`         VARCHAR(255)     DEFAULT NULL COMMENT '请求方法(类名.方法名)',
    `request_uri`    VARCHAR(255)     DEFAULT NULL COMMENT '请求地址',
    `request_method` VARCHAR(10)      DEFAULT NULL COMMENT '请求方式(GET/POST...)',
    `params`         TEXT             DEFAULT NULL COMMENT '请求参数(已脱敏)',
    `ip`             VARCHAR(64)      DEFAULT NULL COMMENT '操作IP',
    `browser`        VARCHAR(100)     DEFAULT NULL COMMENT '浏览器类型',
    `os`             VARCHAR(100)     DEFAULT NULL COMMENT '操作系统',
    `oper_name`      VARCHAR(50)      DEFAULT NULL COMMENT '操作人',
    `status`         TINYINT NOT NULL DEFAULT 1 COMMENT '操作状态: 1-成功 0-失败',
    `error_msg`      TEXT             DEFAULT NULL COMMENT '异常信息',
    `cost_time`      BIGINT           DEFAULT 0 COMMENT '耗时(毫秒)',
    `oper_time`      DATETIME         DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    `remark`         VARCHAR(500)     DEFAULT NULL COMMENT '备注',
    `create_by`      VARCHAR(64)      DEFAULT NULL COMMENT '创建者',
    `create_time`    DATETIME         DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`      VARCHAR(64)      DEFAULT NULL COMMENT '修改者',
    `update_time`    DATETIME         DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `deleted`        TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_oper_time` (`oper_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='操作日志表';

-- ---------------------------------------------------------------------
-- 8. 文件上传记录表
-- ---------------------------------------------------------------------
CREATE TABLE `sys_file`
(
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `original_name` VARCHAR(255) NOT NULL COMMENT '原始文件名',
    `file_name`     VARCHAR(255) NOT NULL COMMENT '存储文件名',
    `file_path`     VARCHAR(500) NOT NULL COMMENT '物理存储路径',
    `url`           VARCHAR(500) NOT NULL COMMENT '访问地址',
    `file_size`     BIGINT       NOT NULL DEFAULT 0 COMMENT '文件大小(字节)',
    `file_type`     VARCHAR(100)          DEFAULT NULL COMMENT '文件类型(扩展名)',
    `biz_type`      VARCHAR(50)           DEFAULT NULL COMMENT '业务类型(avatar/kb/chat)',
    `remark`        VARCHAR(500)          DEFAULT NULL COMMENT '备注',
    `create_by`     VARCHAR(64)           DEFAULT NULL COMMENT '创建者',
    `create_time`   DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     VARCHAR(64)           DEFAULT NULL COMMENT '修改者',
    `update_time`   DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_file_biz` (`biz_type`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='文件上传记录表';

-- ---------------------------------------------------------------------
-- 9. 保险知识库表
-- ---------------------------------------------------------------------
CREATE TABLE `kb_question`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `category`    VARCHAR(30)  NOT NULL DEFAULT 'OTHER' COMMENT '业务分类: POLICY-投保 ENDORSE-保全 SURRENDER-退保 REDUCE-减保 CLAIM-理赔 LOAN-保单贷款 OTHER-其他',
    `question`    VARCHAR(500) NOT NULL COMMENT '标准问题',
    `answer`      TEXT         NOT NULL COMMENT '标准答案',
    `keywords`    VARCHAR(500)          DEFAULT NULL COMMENT '关键词(逗号分隔,用于召回)',
    `hit_count`   INT          NOT NULL DEFAULT 0 COMMENT '命中次数',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 1-启用 0-停用',
    `remark`      VARCHAR(500)          DEFAULT NULL COMMENT '备注',
    `create_by`   VARCHAR(64)           DEFAULT NULL COMMENT '创建者',
    `create_time` DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   VARCHAR(64)           DEFAULT NULL COMMENT '修改者',
    `update_time` DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_kb_category` (`category`),
    KEY `idx_kb_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='保险知识库表';

-- ---------------------------------------------------------------------
-- 10. AI 会话表
-- ---------------------------------------------------------------------
CREATE TABLE `chat_session`
(
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `cus_user_id`       BIGINT       NOT NULL COMMENT '所属客户ID(cus_user.id)',
    `title`             VARCHAR(100) NOT NULL DEFAULT '新会话' COMMENT '会话标题',
    `service_mode`      TINYINT      NOT NULL DEFAULT 0 COMMENT '服务模式:0-AI自助 1-排队等人工 2-人工接入中',
    `handler_id`        BIGINT                DEFAULT NULL COMMENT '接管坐席ID(sys_user.id)',
    `handler_name`      VARCHAR(50)           DEFAULT NULL COMMENT '接管坐席姓名',
    `ticket_id`         BIGINT                DEFAULT NULL COMMENT '关联工单ID(svc_ticket.id)',
    `last_message`      VARCHAR(500)          DEFAULT NULL COMMENT '最后一条消息摘要',
    `message_count`     INT          NOT NULL DEFAULT 0 COMMENT '消息条数',
    `last_message_time` DATETIME              DEFAULT NULL COMMENT '最后消息时间',
    `remark`            VARCHAR(500)          DEFAULT NULL COMMENT '备注',
    `create_by`         VARCHAR(64)           DEFAULT NULL COMMENT '创建者',
    `create_time`       DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`         VARCHAR(64)           DEFAULT NULL COMMENT '修改者',
    `update_time`       DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `deleted`           TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_session_cus_user` (`cus_user_id`),
    KEY `idx_session_last_time` (`last_message_time`),
    KEY `idx_session_service_mode` (`service_mode`),
    KEY `idx_session_ticket` (`ticket_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='AI 会话表';

-- ---------------------------------------------------------------------
-- 11. AI 会话消息表
-- ---------------------------------------------------------------------
CREATE TABLE `chat_message`
(
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `session_id`  BIGINT      NOT NULL COMMENT '会话ID',
    `cus_user_id` BIGINT      NOT NULL COMMENT '所属客户ID',
    `role`        VARCHAR(20) NOT NULL COMMENT '消息角色: system/user/assistant/staff',
    `msg_type`    VARCHAR(20) NOT NULL DEFAULT 'text' COMMENT '消息类型:text/image/video/system',
    `content`     TEXT        NOT NULL COMMENT '消息内容',
    `media_url`   VARCHAR(500)         DEFAULT NULL COMMENT '媒体地址(图片/视频)',
    `media_name`  VARCHAR(200)         DEFAULT NULL COMMENT '原始文件名',
    `media_size`  BIGINT      NOT NULL DEFAULT 0 COMMENT '文件大小(字节)',
    `sender_name` VARCHAR(50)          DEFAULT NULL COMMENT '发送者显示名(坐席姓名)',
    `model`       VARCHAR(50)          DEFAULT NULL COMMENT '生成模型(assistant 消息)',
    `tokens`      INT         NOT NULL DEFAULT 0 COMMENT '消耗 token 数',
    `remark`      VARCHAR(500)         DEFAULT NULL COMMENT '备注',
    `create_by`   VARCHAR(64)          DEFAULT NULL COMMENT '创建者',
    `create_time` DATETIME             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   VARCHAR(64)          DEFAULT NULL COMMENT '修改者',
    `update_time` DATETIME             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `deleted`     TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_msg_session` (`session_id`),
    KEY `idx_msg_cus_user` (`cus_user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='AI 会话消息表';

-- =====================================================================
-- 初始化数据
-- =====================================================================

-- 角色：1-超级管理员（全部菜单） 2-客服专员（业务菜单）
INSERT INTO `sys_role` (`id`, `role_code`, `role_name`, `sort`, `status`, `remark`, `create_by`)
VALUES (1, 'SUPER_ADMIN', '超级管理员', 1, 1, '拥有全部菜单与操作权限', 'system'),
       (2, 'OPERATOR', '客服专员', 2, 1, '负责客户咨询与知识库维护', 'system');

-- 管理端账号：admin / 123456、operator / 123456
INSERT INTO `sys_user` (`id`, `username`, `password`, `real_name`, `phone`, `email`, `role_id`, `status`, `remark`,
                        `create_by`)
VALUES (1, 'admin', '$2a$10$0WDE7TP9Voa.ZJX6vaufEuAojK5HD0YQYECOhJI6KABeycNCptKUi', '超级管理员', '13800000000',
        'admin@insure.com', 1, 1, '系统内置超级管理员账号', 'system'),
       (2, 'operator', '$2a$10$0WDE7TP9Voa.ZJX6vaufEuAojK5HD0YQYECOhJI6KABeycNCptKUi', '客服专员-李静', '13800000009',
        'operator@insure.com', 2, 1, '演示用客服坐席账号', 'system');

-- 后台菜单：数据概览 / 客户服务 / 知识库 / 系统管理 / 日志中心
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `icon`, `perms`, `sort`,
                        `visible`, `status`, `create_by`)
VALUES (1, 0, '数据概览', 'MENU', '/dashboard', 'admin/dashboard/index', 'DataLine', 'dashboard:view', 1, 1, 1, 'system'),
       (10, 0, '客户服务', 'DIR', '/service', NULL, 'ChatDotRound', NULL, 2, 1, 1, 'system'),
       (11, 10, '客户列表', 'MENU', '/service/customer', 'admin/customer/index', 'User', 'customer:list', 1, 1, 1,
        'system'),
       (20, 0, '知识库管理', 'MENU', '/kb', 'admin/kb/index', 'Reading', 'kb:list', 3, 1, 1, 'system'),
       (30, 0, '系统管理', 'DIR', '/system', NULL, 'Setting', NULL, 4, 1, 1, 'system'),
       (31, 30, '管理员账号', 'MENU', '/system/user', 'admin/user/index', 'Avatar', 'sys:user:list', 1, 1, 1, 'system'),
       (32, 30, '角色管理', 'MENU', '/system/role', 'admin/role/index', 'UserFilled', 'sys:role:list', 2, 1, 1,
        'system'),
       (33, 30, '菜单管理', 'MENU', '/system/menu', 'admin/menu/index', 'Menu', 'sys:menu:list', 3, 1, 1, 'system'),
       (40, 0, '日志中心', 'DIR', '/log', NULL, 'Document', NULL, 5, 1, 1, 'system'),
       (41, 40, '登录日志', 'MENU', '/log/login', 'admin/log/login', 'Key', 'log:login:list', 1, 1, 1, 'system'),
       (42, 40, '操作日志', 'MENU', '/log/oper', 'admin/log/oper', 'Tickets', 'log:oper:list', 2, 1, 1, 'system');

-- 角色菜单：超管全部；客服专员仅业务相关菜单
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`, `create_by`)
VALUES (1, 1, 'system'), (1, 10, 'system'), (1, 11, 'system'), (1, 20, 'system'), (1, 30, 'system'),
       (1, 31, 'system'), (1, 32, 'system'), (1, 33, 'system'), (1, 40, 'system'), (1, 41, 'system'),
       (1, 42, 'system'),
       (2, 1, 'system'), (2, 10, 'system'), (2, 11, 'system'), (2, 20, 'system'), (2, 40, 'system'),
       (2, 41, 'system'), (2, 42, 'system');

-- 客户端演示账号：13900000001 / 123456
INSERT INTO `cus_user` (`id`, `username`, `password`, `nickname`, `real_name`, `phone`, `email`, `gender`, `status`,
                        `register_source`, `remark`, `create_by`)
VALUES (1, 'customer01', '$2a$10$0WDE7TP9Voa.ZJX6vaufEuAojK5HD0YQYECOhJI6KABeycNCptKUi', '小张', '张三', '13900000001',
        'customer01@example.com', 1, 1, 'H5', '演示用客户账号', 'system');

-- 保险知识库初始语料
INSERT INTO `kb_question` (`category`, `question`, `answer`, `keywords`, `hit_count`, `status`, `create_by`)
VALUES ('POLICY', '投保需要满足什么条件?',
        '投保时需满足以下基本条件:1) 投保人需年满 18 周岁且具有完全民事行为能力;2) 被保险人的年龄需在条款约定的投保年龄范围内;3) 投保人与被保险人需存在保险利益关系(本人、配偶、子女、父母);4) 如实填写健康告知,既往病史需如实说明,否则可能影响后续理赔。',
        '投保,条件,年龄,健康告知,保险利益', 0, 1, 'system'),
       ('POLICY', '投保后多久生效?',
        '一般情况下,保单在保费到账且承保通过后的次日零时生效;意外险等部分产品可约定生效时间。等待期(观察期)通常为合同生效后的 90 天或 180 天,等待期内因疾病出险不承担赔付责任,具体以条款约定为准。',
        '生效,等待期,观察期,承保', 0, 1, 'system'),
       ('ENDORSE', '保全是什么?可以办理哪些保全业务?',
        '保全指保险合同生效后,对合同内容进行的变更与维护。常见保全业务包括:联系方式与地址变更、受益人变更、保额调整、缴费方式变更、保单复效、保单贷款、红利领取等。可通过官方 APP、微信公众号或线下柜面办理。',
        '保全,变更,受益人,复效,联系方式', 0, 1, 'system'),
       ('ENDORSE', '如何变更受益人?',
        '变更受益人需由投保人提出申请,并经被保险人同意。可通过官方 APP 的"保全服务-受益人变更"在线办理,或携带投保人、被保险人身份证件到柜面办理。变更自保险公司批注或出具批单之日起生效。',
        '受益人,变更,保全,批单', 0, 1, 'system'),
       ('SURRENDER', '退保能退多少钱?犹豫期是多久?',
        '犹豫期一般为签收合同之日起 15 日内(部分产品为 20 日)。犹豫期内退保,保险公司在扣除工本费后退还全部已交保费;犹豫期后退保按保单现金价值退还,保障型产品前期现金价值较低,可能存在较大损失。建议退保前先评估保障缺口,避免保障中断。',
        '退保,现金价值,犹豫期,损失', 0, 1, 'system'),
       ('REDUCE', '减保是什么意思?怎么办理?',
        '减保指在保险期间内降低基本保险金额,从而减少后续应缴保费,保单继续有效。办理方式:通过官方 APP 提交减保申请,或到柜面填写变更申请书。减保后保额不得低于条款约定的最低承保金额,且部分产品对减保次数与时间有限制。',
        '减保,降低保额,减少保费', 0, 1, 'system'),
       ('CLAIM', '理赔流程是怎样的?需要准备哪些材料?',
        '理赔流程:1) 出险后 10 日内拨打客服热线或通过 APP 报案;2) 准备理赔材料;3) 提交材料并等待审核;4) 审核通过后赔款转入指定账户。常见材料包括:理赔申请书、身份证件、银行卡、病历与诊断证明、医疗费用发票及清单、出院小结等。小额案件一般 3-5 个工作日结案。',
        '理赔,报案,材料,审核,赔款', 0, 1, 'system'),
       ('LOAN', '保单贷款最多能贷多少?',
        '保单贷款额度一般为保单现金价值的 80%,具体比例以条款约定为准。贷款期限通常为 6 个月,可续贷;贷款利率按保险公司公布的利率执行。贷款本息未偿还且超过现金价值时,合同可能中止,请按时还款。',
        '保单贷款,现金价值,额度,利率', 0, 1, 'system');
