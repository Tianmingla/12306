package com.lalal.modules.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话记忆实体 - MySQL持久化
 * 存储多轮对话的完整消息历史
 */
@Data
@TableName("t_agent_memory")
public class AgentMemoryDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话ID */
    private String conversationId;

    /** 用户ID */
    private String userId;

    /** 消息角色：user / assistant / system / tool */
    private String role;

    /** 消息内容（Kryo序列化后的二进制） */
    private byte[] content;

    /** 消息类型：text / tool_call / tool_result */
    private String messageType;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
