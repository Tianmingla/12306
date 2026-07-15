package com.lalal.modules.agent;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 工具调用死循环检测器
 *
 * 检测 Agent 重复调用同一工具的死循环场景：
 * 1. 连续多次调用同一工具（如反复查询同一车次）
 * 2. 两个工具互相触发（如 A→B→A→B...）
 * 3. 总工具调用次数超过限制
 *
 * 每个 conversationId 对应一个独立的检测器实例
 *
 * 使用方式：
 * <pre>
 * ToolCallLoopDetector detector = new ToolCallLoopDetector(maxSteps, maxSameToolCalls);
 * detector.record("searchDirectTrains");
 * if (detector.isLoopDetected()) {
 *     // 终止 Agent 执行
 * }
 * </pre>
 */
@Slf4j
public class ToolCallLoopDetector {

    /** 最大总步骤数（所有工具调用总和） */
    private final int maxTotalSteps;

    /** 同一工具最大连续调用次数 */
    private final int maxSameToolCalls;

    /** 工具调用历史 */
    private final List<String> callHistory = new ArrayList<>();

    /** 每个工具的调用计数 */
    private final ConcurrentHashMap<String, AtomicInteger> toolCallCounts = new ConcurrentHashMap<>();

    /** 是否已检测到死循环 */
    private volatile boolean loopDetected = false;

    /** 检测到的死循环原因 */
    private String loopReason;

    public ToolCallLoopDetector(int maxTotalSteps, int maxSameToolCalls) {
        this.maxTotalSteps = maxTotalSteps;
        this.maxSameToolCalls = maxSameToolCalls;
    }

    /**
     * 记录一次工具调用
     *
     * @param toolName 工具名称
     * @return 是否继续执行（true=正常，false=检测到死循环）
     */
    public boolean record(String toolName) {
        callHistory.add(toolName);
        toolCallCounts.computeIfAbsent(toolName, k -> new AtomicInteger(0)).incrementAndGet();

        // 检测1：总步骤数超限
        if (callHistory.size() > maxTotalSteps) {
            loopDetected = true;
            loopReason = String.format("总步骤数超过限制(%d > %d)，可能陷入死循环", callHistory.size(), maxTotalSteps);
            log.warn("Loop detected: {}", loopReason);
            return false;
        }

        // 检测2：同一工具调用次数超限
        int sameToolCount = toolCallCounts.get(toolName).get();
        if (sameToolCount > maxSameToolCalls) {
            loopDetected = true;
            loopReason = String.format("工具 %s 被调用 %d 次，超过限制 %d，可能陷入死循环",
                    toolName, sameToolCount, maxSameToolCalls);
            log.warn("Loop detected: {}", loopReason);
            return false;
        }

        // 检测3：连续相同工具调用模式（如 A→A→A）
        if (callHistory.size() >= 3) {
            int size = callHistory.size();
            String last = callHistory.get(size - 1);
            String secondLast = callHistory.get(size - 2);
            String thirdLast = callHistory.get(size - 3);
            if (last.equals(secondLast) && secondLast.equals(thirdLast)) {
                loopDetected = true;
                loopReason = String.format("连续3次调用同一工具 %s，检测到死循环", last);
                log.warn("Loop detected: {}", loopReason);
                return false;
            }
        }

        // 检测4：AB交替模式（A→B→A→B→A→B）
        if (callHistory.size() >= 6) {
            int size = callHistory.size();
            String a = callHistory.get(size - 1);
            String b = callHistory.get(size - 2);
            if (a.equals(callHistory.get(size - 3))
                    && b.equals(callHistory.get(size - 4))
                    && a.equals(callHistory.get(size - 5))
                    && b.equals(callHistory.get(size - 6))) {
                loopDetected = true;
                loopReason = String.format("工具 %s 和 %s 交替调用，检测到死循环", a, b);
                log.warn("Loop detected: {}", loopReason);
                return false;
            }
        }

        return true;
    }

    /**
     * 是否检测到死循环
     */
    public boolean isLoopDetected() {
        return loopDetected;
    }

    /**
     * 获取死循环原因
     */
    public String getLoopReason() {
        return loopReason;
    }

    /**
     * 获取当前总步骤数
     */
    public int getTotalSteps() {
        return callHistory.size();
    }

    /**
     * 获取工具调用历史
     */
    public List<String> getCallHistory() {
        return List.copyOf(callHistory);
    }

    /**
     * 获取当前步骤的摘要（用于日志和调试）
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Steps: ").append(callHistory.size()).append("/").append(maxTotalSteps);
        if (!callHistory.isEmpty()) {
            sb.append(", Recent: ");
            int start = Math.max(0, callHistory.size() - 5);
            for (int i = start; i < callHistory.size(); i++) {
                if (i > start) sb.append(" → ");
                sb.append(callHistory.get(i));
            }
        }
        return sb.toString();
    }
}
