package com.lalal.modules.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 集中式工具注册管理器（单例模式）
 * 统一管理所有 @Tool 工具的注册，供 ChatClient 调用时使用
 *
 * Spring AI 1.1.0: 工具对象通过 ToolCallbackProvider 注册，
 * ChatClient 使用 .defaultToolCallbacks() 而非 .defaultTools()
 */
@Slf4j
@Component
public class ToolRegistry {

    private final List<Object> toolObjects = new ArrayList<>();
    private volatile ToolCallbackProvider cachedProvider = null;

    /**
     * 注册一个工具提供者（包含 @Tool 注解方法的类）
     * 在每个工具类初始化后调用
     */
    public synchronized void register(Object toolProvider) {
        toolObjects.add(toolProvider);
        cachedProvider = null;  // 清缓存，下次获取时重建
        // 获取工具名用于日志
        try {
            ToolCallbackProvider provider = ToolCallbackProvider.from(ToolCallbacks.from(toolProvider));
            for (ToolCallback cb : provider.getToolCallbacks()) {
                log.info("Registered tool: {}", cb.getToolDefinition().name());
            }
        } catch (Exception e) {
            log.warn("Failed to introspect tool: {}", e.getMessage());
        }
    }

    /**
     * 获取包含所有工具的 ToolCallbackProvider
     * 用于 ChatClient 的 .defaultToolCallbacks() 或 .toolCallbacks()
     */
    public ToolCallbackProvider getToolCallbackProvider() {
        if (cachedProvider == null) {
            synchronized (this) {
                if (cachedProvider == null) {
                    cachedProvider = ToolCallbackProvider.from(ToolCallbacks.from(toolObjects.toArray()));
                }
            }
        }
        return cachedProvider;
    }

    /**
     * 获取所有已注册的工具回调
     * @deprecated 使用 getToolCallbackProvider() 代替
     */
    @Deprecated
    public ToolCallback[] getAllToolCallbacks() {
        return getToolCallbackProvider().getToolCallbacks();
    }

    /**
     * 获取已注册工具数量
     */
    public int getToolCount() {
        return getToolCallbackProvider().getToolCallbacks().length;
    }

    /**
     * 获取所有工具名称
     */
    public List<String> getToolNames() {
        return List.of(getToolCallbackProvider().getToolCallbacks()).stream()
                .map(cb -> cb.getToolDefinition().name())
                .toList();
    }
}
