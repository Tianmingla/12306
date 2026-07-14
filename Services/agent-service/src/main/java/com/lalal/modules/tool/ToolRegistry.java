package com.lalal.modules.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 集中式工具注册管理器（单例模式）
 * 统一管理所有 @Tool 工具的注册，供 ChatClient 调用时使用
 *
 * 使用方式：
 * <pre>
 * // 在 ChatClient 构建时注册所有工具
 * ChatClient.builder(chatModel)
 *     .defaultTools(toolRegistry.getAllToolCallbacks())
 *     .build();
 * </pre>
 */
@Slf4j
@Component
public class ToolRegistry {

    private final List<ToolCallback> allToolCallbacks = new ArrayList<>();

    /**
     * 注册一个工具提供者（包含 @Tool 注解方法的类）
     * 在每个工具类初始化后调用
     */
    public synchronized void register(Object toolProvider) {
        ToolCallback[] callbacks = MethodToolCallbackProvider.builder()
                .toolObjects(toolProvider)
                .build()
                .getToolCallbacks();

        for (ToolCallback callback : callbacks) {
            allToolCallbacks.add(callback);
            log.info("Registered tool: {}", callback.getToolDefinition().name());
        }
    }

    /**
     * 获取所有已注册的工具回调
     */
    public ToolCallback[] getAllToolCallbacks() {
        return allToolCallbacks.toArray(new ToolCallback[0]);
    }

    /**
     * 获取已注册工具数量
     */
    public int getToolCount() {
        return allToolCallbacks.size();
    }

    /**
     * 获取所有工具名称
     */
    public List<String> getToolNames() {
        return allToolCallbacks.stream()
                .map(cb -> cb.getToolDefinition().name())
                .toList();
    }
}
