package com.lalal.modules.tool;

import lombok.Data;

/**
 * 用户身份上下文
 * 通过 ToolContext 传递给 @Tool 方法
 * 从 Gateway 注入的 X-User-Id 请求头解析
 */
@Data
public class UserContext {

    /** 用户ID */
    private String userId;

    /** 用户名（手机号） */
    private String userName;

    /** 是否已认证 */
    private boolean authenticated;

    public static UserContext of(String userId, String userName) {
        UserContext ctx = new UserContext();
        ctx.setUserId(userId);
        ctx.setUserName(userName);
        ctx.setAuthenticated(userId != null && !userId.isBlank());
        return ctx;
    }

    /**
     * 检查是否已认证，未认证则抛出异常
     */
    public void requireAuthenticated() {
        if (!authenticated) {
            throw new IllegalStateException("请先登录后再执行此操作");
        }
    }
}
