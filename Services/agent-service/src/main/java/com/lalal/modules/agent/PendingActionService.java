package com.lalal.modules.agent;

import com.alibaba.fastjson2.JSON;
import com.lalal.modules.entity.AgentPendingActionDO;
import com.lalal.modules.mapper.AgentPendingActionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 待确认操作服务 — Human-in-the-Loop 核心实现
 *
 * 工作流程：
 * 1. Agent 调用敏感工具（退票/改签/购票/取消）时，不立即执行，而是创建待确认记录
 * 2. 返回确认信息给用户，等待用户确认或拒绝
 * 3. 用户确认后，执行实际操作
 * 4. 用户拒绝或超时后，取消操作
 *
 * 与 @Tool 方法的集成：
 * 敏感工具在执行前调用 pendingActionService.createPendingAction() 创建待确认记录，
 * 返回确认提示给 AI，AI 再通过 SSE 发送 confirm 事件给前端。
 * 用户确认后，前端调用 /api/agent/confirm/{confirmId}，服务执行实际操作。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PendingActionService {

    private final AgentPendingActionMapper pendingActionMapper;

    /** 待确认操作默认过期时间（分钟） */
    private static final int DEFAULT_EXPIRE_MINUTES = 30;

    /**
     * 创建待确认操作
     *
     * @param userId        用户ID
     * @param conversationId 会话ID
     * @param actionType    操作类型：refund_ticket / cancel_order / purchase_ticket / change_ticket
     * @param actionParams  操作参数（将被序列化为JSON）
     * @return 确认ID
     */
    public String createPendingAction(String userId, String conversationId,
                                       String actionType, Map<String, Object> actionParams) {
        String confirmId = UUID.randomUUID().toString().replace("-", "");

        AgentPendingActionDO pendingAction = new AgentPendingActionDO();
        pendingAction.setConfirmId(confirmId);
        pendingAction.setUserId(userId);
        pendingAction.setConversationId(conversationId);
        pendingAction.setActionType(actionType);
        pendingAction.setActionParams(JSON.toJSONString(actionParams));
        pendingAction.setStatus("pending");
        pendingAction.setCreateTime(LocalDateTime.now());
        pendingAction.setExpireTime(LocalDateTime.now().plusMinutes(DEFAULT_EXPIRE_MINUTES));

        pendingActionMapper.insert(pendingAction);
        log.info("Created pending action: confirmId={}, type={}, userId={}", confirmId, actionType, userId);

        return confirmId;
    }

    /**
     * 确认操作
     *
     * @param confirmId 确认ID
     * @param userId    用户ID（校验权限）
     * @return 待确认操作实体（包含操作类型和参数），如果无效返回 null
     */
    public AgentPendingActionDO confirmAction(String confirmId, String userId) {
        AgentPendingActionDO action = findValidAction(confirmId, userId);
        if (action == null) return null;

        action.setStatus("confirmed");
        pendingActionMapper.updateById(action);
        log.info("Confirmed pending action: confirmId={}, type={}", confirmId, action.getActionType());
        return action;
    }

    /**
     * 拒绝操作
     *
     * @param confirmId 确认ID
     * @param userId    用户ID（校验权限）
     * @return 被拒绝的操作，如果无效返回 null
     */
    public AgentPendingActionDO rejectAction(String confirmId, String userId) {
        AgentPendingActionDO action = findValidAction(confirmId, userId);
        if (action == null) return null;

        action.setStatus("rejected");
        pendingActionMapper.updateById(action);
        log.info("Rejected pending action: confirmId={}, type={}", confirmId, action.getActionType());
        return action;
    }

    /**
     * 获取待确认操作
     */
    public AgentPendingActionDO getPendingAction(String confirmId) {
        return pendingActionMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AgentPendingActionDO>()
                        .eq(AgentPendingActionDO::getConfirmId, confirmId)
        );
    }

    /**
     * 检查操作类型是否需要人工确认
     */
    public boolean requiresConfirmation(String actionType) {
        return switch (actionType) {
            case "refund_ticket", "cancel_order", "purchase_ticket", "change_ticket" -> true;
            default -> false;
        };
    }

    /**
     * 获取操作的中文描述
     */
    public String getActionDescription(String actionType) {
        return switch (actionType) {
            case "refund_ticket" -> "退票";
            case "cancel_order" -> "取消订单";
            case "purchase_ticket" -> "购票";
            case "change_ticket" -> "改签";
            default -> "操作";
        };
    }

    /**
     * 查找有效的待确认操作（pending 状态且未过期）
     */
    private AgentPendingActionDO findValidAction(String confirmId, String userId) {
        AgentPendingActionDO action = getPendingAction(confirmId);

        if (action == null) {
            log.warn("Pending action not found: confirmId={}", confirmId);
            return null;
        }

        // 校验用户权限
        if (userId != null && !userId.equals(action.getUserId())) {
            log.warn("User mismatch for pending action: confirmId={}, expectedUser={}, actualUser={}",
                    confirmId, action.getUserId(), userId);
            return null;
        }

        // 校验状态
        if (!"pending".equals(action.getStatus())) {
            log.warn("Pending action already processed: confirmId={}, status={}", confirmId, action.getStatus());
            return null;
        }

        // 校验过期
        if (action.getExpireTime() != null && LocalDateTime.now().isAfter(action.getExpireTime())) {
            action.setStatus("expired");
            pendingActionMapper.updateById(action);
            log.warn("Pending action expired: confirmId={}", confirmId);
            return null;
        }

        return action;
    }
}
