package com.lalal.modules.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 待确认操作实体 - Human-in-the-Loop
 * 存储需要人工确认的敏感操作
 */
@Data
@TableName("t_agent_pending_action")
public class AgentPendingActionDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 确认ID（UUID） */
    private String confirmId;

    /** 用户ID */
    private String userId;

    /** 会话ID */
    private String conversationId;

    /** 操作类型：purchase_ticket / refund_ticket / cancel_order / change_ticket */
    private String actionType;

    /** 操作参数（JSON格式） */
    private String actionParams;

    /** 状态：pending / confirmed / rejected / expired */
    private String status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 过期时间 */
    private LocalDateTime expireTime;
}
