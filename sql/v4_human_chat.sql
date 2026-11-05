-- [字符集] 强制客户端连接使用 utf8mb4。
-- Docker 首次初始化时 mysql 客户端默认可能是 latin1, 会把 UTF-8 中文双重编码入库(页面表现为乱码)。
SET NAMES utf8mb4;

-- =====================================================================
-- v4: 人工客服接管会话 + 会话内图片/视频消息
-- 背景:此前工单只存了 session_id,坐席接单后看不到上下文、也无法回复,
--      只能把结论文本写进 svc_ticket.reply,客户要等结单才知道结果。
-- 本次把「工单」与「会话」真正连起来:接单即接管客户原来那个会话。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 会话:服务模式与接管坐席
-- service_mode: 0-AI 自助 1-已转人工待接入(AI 仍可答) 2-人工接入中(AI 静默)
-- ---------------------------------------------------------------------
ALTER TABLE chat_session
    ADD COLUMN service_mode TINYINT NOT NULL DEFAULT 0 COMMENT '服务模式:0-AI自助 1-排队等人工 2-人工接入中' AFTER title,
    ADD COLUMN handler_id BIGINT DEFAULT NULL COMMENT '接管坐席ID(sys_user.id)' AFTER service_mode,
    ADD COLUMN handler_name VARCHAR(50) DEFAULT NULL COMMENT '接管坐席姓名' AFTER handler_id,
    ADD COLUMN ticket_id BIGINT DEFAULT NULL COMMENT '关联工单ID(svc_ticket.id)' AFTER handler_name;

-- 坐席工作台按「人工中」筛选会话,以及按工单反查会话
ALTER TABLE chat_session
    ADD INDEX idx_session_service_mode (service_mode),
    ADD INDEX idx_session_ticket (ticket_id);

-- ---------------------------------------------------------------------
-- 2. 消息:消息类型与媒体信息
-- role 新增 staff(人工客服),system 用于「客服已接入」等系统提示
-- ---------------------------------------------------------------------
ALTER TABLE chat_message
    ADD COLUMN msg_type VARCHAR(20) NOT NULL DEFAULT 'text' COMMENT '消息类型:text/image/video/system' AFTER role,
    ADD COLUMN media_url VARCHAR(500) DEFAULT NULL COMMENT '媒体地址(图片/视频)' AFTER content,
    ADD COLUMN media_name VARCHAR(200) DEFAULT NULL COMMENT '原始文件名' AFTER media_url,
    ADD COLUMN media_size BIGINT NOT NULL DEFAULT 0 COMMENT '文件大小(字节)' AFTER media_name,
    ADD COLUMN sender_name VARCHAR(50) DEFAULT NULL COMMENT '发送者显示名(坐席姓名)' AFTER media_size;
