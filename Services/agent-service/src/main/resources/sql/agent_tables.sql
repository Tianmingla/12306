-- =============================================
-- Agent Service 数据库表
-- =============================================

-- 对话记忆表（MySQL持久化，配合Redis缓存 + Kryo序列化）
CREATE TABLE IF NOT EXISTS `t_agent_memory` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `conversation_id` VARCHAR(64) NOT NULL COMMENT '会话ID',
    `user_id` VARCHAR(64) DEFAULT NULL COMMENT '用户ID',
    `role` VARCHAR(20) NOT NULL COMMENT '消息角色：user/assistant/system/tool',
    `content` MEDIUMBLOB NOT NULL COMMENT '消息内容（Kryo序列化二进制）',
    `message_type` VARCHAR(20) DEFAULT 'text' COMMENT '消息类型：text/tool_call/tool_result',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_conversation_id` (`conversation_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI Agent对话记忆表';

-- 待确认操作表（Human-in-the-Loop）
CREATE TABLE IF NOT EXISTS `t_agent_pending_action` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `confirm_id` VARCHAR(64) NOT NULL COMMENT '确认ID（UUID）',
    `user_id` VARCHAR(64) NOT NULL COMMENT '用户ID',
    `conversation_id` VARCHAR(64) NOT NULL COMMENT '会话ID',
    `action_type` VARCHAR(50) NOT NULL COMMENT '操作类型：purchase_ticket/refund_ticket/cancel_order/change_ticket',
    `action_params` JSON DEFAULT NULL COMMENT '操作参数（JSON格式）',
    `status` VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '状态：pending/confirmed/rejected/expired',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `expire_time` DATETIME NOT NULL COMMENT '过期时间',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_confirm_id` (`confirm_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI Agent待确认操作表';
