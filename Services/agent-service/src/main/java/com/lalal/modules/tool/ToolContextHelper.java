package com.lalal.modules.tool;

import org.springframework.ai.chat.model.ToolContext;

import java.util.Map;

/**
 * ToolContext 工具类
 * 从 Spring AI 的 ToolContext 中提取用户身份信息
 *
 * 使用方式：在 @Tool 方法中通过 ToolContext 参数获取
 * <pre>
 * {@code
 * @Tool(description = "查询订单")
 * public String queryOrder(String orderSn, ToolContext toolContext) {
 *     UserContext user = ToolContextHelper.getUserContext(toolContext);
 *     user.requireAuthenticated();
 *     ...
 * }
 * }
 * </pre>
 */
public class ToolContextHelper {

    /** ToolContext 中 UserContext 的 Key */
    public static final String USER_CONTEXT_KEY = "userContext";

    /**
     * 从 ToolContext 提取 UserContext
     */
    public static UserContext getUserContext(ToolContext toolContext) {
        if (toolContext == null) {
            return UserContext.of(null, null);
        }
        Map<String, Object> context = toolContext.getContext();
        if (context == null) {
            return UserContext.of(null, null);
        }
        Object userCtx = context.get(USER_CONTEXT_KEY);
        if (userCtx instanceof UserContext uc) {
            return uc;
        }
        return UserContext.of(null, null);
    }

    /**
     * 构建 ToolContext
     * 在 AgentServiceImpl 中调用 ChatClient 时传入
     */
    public static ToolContext buildToolContext(String userId, String userName) {
        UserContext userContext = UserContext.of(userId, userName);
        ToolContext toolContext = new ToolContext(Map.of(USER_CONTEXT_KEY, userContext));
        return toolContext;
    }
}
