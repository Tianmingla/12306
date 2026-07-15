package com.lalal.modules.agent;

import com.lalal.modules.config.AgentProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 执行管理器
 *
 * 整合 Step 7 的三大核心能力：
 * 1. 死循环检测 — ToolCallLoopDetector
 * 2. Human-in-the-Loop — PendingActionService
 * 3. 步骤限制 — maxSteps 配置
 *
 * 每个会话(conversationId)维护独立的执行状态，
 * 包括工具调用历史、步骤计数和死循环检测结果。
 *
 * 设计理念：
 * Spring AI ChatClient + ToolCallingAdvisor 已经实现了 ReAct 模式（思考→行动→观察循环），
 * 我们不需要重新造轮子写自己的 ReAct 循环。
 * AgentExecutor 作为"执行管理器"在 ReAct 循环外层提供安全保障：
 * - 防止死循环（同一工具反复调用）
 * - 防止无限步骤（总步骤数超限）
 * - 敏感操作拦截（退票/改签等需确认）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentExecutor {

    private final AgentProperties agentProperties;
    private final PendingActionService pendingActionService;

    /** 每个会话的死循环检测器 */
    private final Map<String, ToolCallLoopDetector> loopDetectors = new ConcurrentHashMap<>();

    /**
     * 获取待确认操作服务（供 AgentServiceImpl 使用）
     */
    public PendingActionService getPendingActionService() {
        return pendingActionService;
    }

    /**
     * 为会话创建或获取死循环检测器
     *
     * @param conversationId 会话ID
     * @return 死循环检测器
     */
    public ToolCallLoopDetector getOrCreateLoopDetector(String conversationId) {
        return loopDetectors.computeIfAbsent(conversationId, id -> {
            AgentProperties.AgentConfig config = agentProperties.getAgent();
            int maxSteps = config.getMaxSteps();
            // 同一工具最大调用次数 = 总步骤数 / 2，但至少3次
            int maxSameToolCalls = Math.max(3, maxSteps / 2);
            log.info("Created loop detector for conversation {}: maxSteps={}, maxSameToolCalls={}",
                    id, maxSteps, maxSameToolCalls);
            return new ToolCallLoopDetector(maxSteps, maxSameToolCalls);
        });
    }

    /**
     * 记录一次工具调用，检测死循环
     *
     * @param conversationId 会话ID
     * @param toolName       工具名称
     * @return true=继续执行，false=检测到死循环应终止
     */
    public boolean recordToolCall(String conversationId, String toolName) {
        ToolCallLoopDetector detector = getOrCreateLoopDetector(conversationId);
        return detector.record(toolName);
    }

    /**
     * 检查工具调用是否需要人工确认
     * 如果需要确认，创建待确认记录并返回确认提示
     *
     * @param conversationId 会话ID
     * @param userId         用户ID
     * @param actionType     操作类型
     * @param actionParams   操作参数
     * @return 确认提示文本（如果需要确认），null 表示不需要确认
     */
    public String checkAndCreateConfirmation(String conversationId, String userId,
                                              String actionType, Map<String, Object> actionParams) {
        if (!agentProperties.getAgent().isHumanInTheLoop()) {
            return null; // 未启用人工确认，直接执行
        }

        if (!pendingActionService.requiresConfirmation(actionType)) {
            return null; // 非敏感操作，不需要确认
        }

        // 创建待确认记录
        String confirmId = pendingActionService.createPendingAction(userId, conversationId, actionType, actionParams);
        String actionDesc = pendingActionService.getActionDescription(actionType);

        String prompt = String.format(
                "⚠️ %s是需要人工确认的敏感操作。\n" +
                "操作类型：%s\n" +
                "操作参数：%s\n\n" +
                "请告知用户此操作需要确认，确认ID为：%s\n" +
                "等待用户确认后再执行。如果用户拒绝，请告知操作已取消。",
                actionDesc, actionDesc, actionParams, confirmId
        );

        log.info("Created confirmation for {}: confirmId={}, userId={}", actionType, confirmId, userId);
        return prompt;
    }

    /**
     * 确认操作
     *
     * @param confirmId 确认ID
     * @param approved  是否批准
     * @param userId    用户ID
     * @return 待确认操作实体（包含操作类型和参数），如果无效返回 null
     */
    public com.lalal.modules.entity.AgentPendingActionDO processConfirmation(String confirmId, boolean approved, String userId) {
        if (approved) {
            return pendingActionService.confirmAction(confirmId, userId);
        } else {
            return pendingActionService.rejectAction(confirmId, userId);
        }
    }

    /**
     * 获取会话的执行摘要
     */
    public String getExecutionSummary(String conversationId) {
        ToolCallLoopDetector detector = loopDetectors.get(conversationId);
        if (detector == null) {
            return "No execution state for conversation: " + conversationId;
        }
        return detector.getSummary();
    }

    /**
     * 清理会话的执行状态
     */
    public void cleanup(String conversationId) {
        loopDetectors.remove(conversationId);
        log.debug("Cleaned up execution state for conversation: {}", conversationId);
    }

    /**
     * 检查是否检测到死循环
     */
    public boolean isLoopDetected(String conversationId) {
        ToolCallLoopDetector detector = loopDetectors.get(conversationId);
        return detector != null && detector.isLoopDetected();
    }

    /**
     * 获取死循环原因
     */
    public String getLoopReason(String conversationId) {
        ToolCallLoopDetector detector = loopDetectors.get(conversationId);
        return detector != null ? detector.getLoopReason() : null;
    }
}
