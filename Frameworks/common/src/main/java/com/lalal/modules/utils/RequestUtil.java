package com.lalal.modules.utils;


import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Cookie;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * HttpServletRequest 上下文工具类
 * 用于从当前线程上下文中获取请求信息、Token、IP 等
 */
public class RequestUtil {

    private static final Logger log = LoggerFactory.getLogger(RequestUtil.class);

    // 常见的 Token Header 键名
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    // 常见的 Token 参数键名
    private static final String[] TOKEN_PARAM_KEYS = {"access_token", "token", "auth_token"};

    /**
     * 获取当前线程绑定的 HttpServletRequest 对象
     * 如果在非 Web 线程（如定时任务）调用，将返回 null
     */
    public static HttpServletRequest getRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            log.warn("No current request found in thread: {}. Are you calling this outside of a web request?", Thread.currentThread().getName());
            return null;
        }
        return attributes.getRequest();
    }

    // ================= Token 获取相关 =================

    /**
     * 从请求中提取 Token
     * 优先级：Header (Authorization) > Parameter > Cookie
     * 自动去除 "Bearer " 前缀
     */
    public static String getToken() {
        HttpServletRequest request = getRequest();
        if (request == null) return null;

        // 1. 尝试从 Header 获取 (最标准的方式)
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header != null && !header.isEmpty()) {
            if (header.startsWith(BEARER_PREFIX)) {
                return header.substring(BEARER_PREFIX.length()).trim();
            }
            // 如果没有 Bearer 前缀，直接返回（兼容某些自定义格式）
            return header.trim();
        }

        // 2. 尝试从 Parameter 获取 (URL 参数或 Form 表单)
        for (String key : TOKEN_PARAM_KEYS) {
            String token = request.getParameter(key);
            if (token != null && !token.isEmpty()) {
                return token.trim();
            }
        }

        // 3. 尝试从 Cookie 获取
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("access_token".equalsIgnoreCase(cookie.getName()) || "token".equalsIgnoreCase(cookie.getName())) {
                    return cookie.getValue().trim();
                }
            }
        }

        return null;
    }

    // ================= 客户端信息相关 =================

    /**
     * 获取客户端真实 IP 地址
     * 兼容 Nginx, SLB, CDN 等反向代理场景
     */
    public static String getClientIp() {
        HttpServletRequest request = getRequest();
        if (request == null) return "unknown";

        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr(); // 最后兜底
        }

        // 如果是多级代理，X-Forwarded-For 会有多个 IP，取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        return ip;
    }

    /**
     * 获取 User-Agent
     */
    public static String getUserAgent() {
        HttpServletRequest request = getRequest();
        return request != null ? request.getHeader("User-Agent") : null;
    }

    /**
     * 获取 Referer
     */
    public static String getReferer() {
        HttpServletRequest request = getRequest();
        return request != null ? request.getHeader("Referer") : null;
    }

    // ================= 请求体与参数相关 =================

    /**
     * 获取请求的所有参数 (Map 形式)
     * 包含 Query Param 和 Form Data (不支持 application/json 的 body)
     */
    public static Map<String, String[]> getAllParams() {
        HttpServletRequest request = getRequest();
        if (request == null) return Collections.emptyMap();
        return request.getParameterMap();
    }

    /**
     * 获取指定参数的值
     */
    public static String getParam(String name) {
        HttpServletRequest request = getRequest();
        return request != null ? request.getParameter(name) : null;
    }

    /**
     * 获取请求体内容 (JSON 字符串)
     * 注意：InputStream 只能读取一次。如果之前已经被过滤器或拦截器读取过，这里可能为空。
     * 建议配合 ContentCachingRequestWrapper 使用。
     */
    public static String getBodyString() {
        HttpServletRequest request = getRequest();
        if (request == null) return null;

        try {
            // 简单的读取方式，适用于未被消费过的流
            // 生产环境建议在 Filter 中使用 ContentCachingRequestWrapper 包装 request
            byte[] buffer = new byte[request.getContentLength()];
            int len = request.getInputStream().read(buffer);
            if (len > 0) {
                return new String(buffer, 0, len, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.error("Failed to read request body", e);
        }
        return null;
    }

    /**
     * 判断是否为 AJAX 请求
     */
    public static boolean isAjaxRequest() {
        HttpServletRequest request = getRequest();
        if (request == null) return false;
        return "XMLHttpRequest".equals(request.getHeader("X-Requested-With"))
                || request.getHeader("Accept") != null && request.getHeader("Accept").contains("application/json");
    }
}