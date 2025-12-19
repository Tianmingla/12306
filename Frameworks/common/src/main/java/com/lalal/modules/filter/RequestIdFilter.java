package com.lalal.modules.filter;

import com.lalal.modules.component.RequestIdGenerator;
import com.lalal.modules.context.RequestContext;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.UUID;
@AllArgsConstructor
public class RequestIdFilter implements Filter {
    RequestIdGenerator generator;
    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws ServletException, IOException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        // 1. 无论前端是否传，都由后端生成（权威）
        String requestId = generator.generate();

        // 2. 放入 MDC（日志）
        MDC.put("requestId", requestId);

        // 3. 放入上下文（供业务代码使用）
        RequestContext.setRequestId(requestId);

        // 4. 响应头返回给前端
        response.setHeader("X-Request-ID", requestId);

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("requestId");
            RequestContext.clear(); // 清理 ThreadLocal
        }
    }
}
