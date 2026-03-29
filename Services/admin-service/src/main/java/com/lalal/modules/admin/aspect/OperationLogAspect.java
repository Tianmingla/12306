package com.lalal.modules.admin.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lalal.modules.admin.annotation.OperationLog;
import com.lalal.modules.admin.dao.OperationLogDO;
import com.lalal.modules.admin.service.AdminLogService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 操作日志切面
 */
@Aspect
@Component
public class OperationLogAspect {

    @Autowired
    private AdminLogService adminLogService;

    @Autowired
    private ObjectMapper objectMapper;

    @Around("@annotation(com.lalal.modules.admin.annotation.OperationLog)")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        long startTime = System.currentTimeMillis();

        // 获取注解信息
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        OperationLog operationLog = method.getAnnotation(OperationLog.class);

        // 获取请求信息
        HttpServletRequest request = getRequest();
        if (request == null) {
            return point.proceed();
        }

        // 构建日志对象
        OperationLogDO logDO = new OperationLogDO();
        logDO.setOperationType(operationLog.type());
        logDO.setModule(operationLog.module());
        logDO.setDescription(operationLog.description());
        logDO.setRequestMethod(request.getMethod());
        logDO.setRequestUrl(request.getRequestURI());
        logDO.setIp(getIpAddress(request));
        logDO.setCreateTime(new Date());

        // 获取操作人信息（从请求头中获取，网关注入）
        String adminIdStr = request.getHeader("X-Admin-Id");
        String adminName = request.getHeader("X-Admin-Name");
        if (adminIdStr != null) {
            logDO.setAdminUserId(Long.parseLong(adminIdStr));
        }
        logDO.setAdminUsername(adminName != null ? adminName : "未知");

        // 记录请求参数
        try {
            Object[] args = point.getArgs();
            Map<String, Object> params = new HashMap<>();
            String[] paramNames = signature.getParameterNames();
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof HttpServletRequest
                        || args[i] instanceof HttpServletResponse
                        || args[i] instanceof MultipartFile) {
                    continue;
                }
                if (paramNames != null && i < paramNames.length) {
                    params.put(paramNames[i], args[i]);
                }
            }
            logDO.setRequestParams(objectMapper.writeValueAsString(params));
        } catch (Exception e) {
            logDO.setRequestParams("参数解析失败");
        }

        // 执行方法
        Object result = null;
        try {
            result = point.proceed();
            logDO.setStatus(0); // 成功
            // 记录响应结果
            try {
                logDO.setResponseResult(objectMapper.writeValueAsString(result));
            } catch (Exception e) {
                logDO.setResponseResult("响应结果解析失败");
            }
        } catch (Throwable e) {
            logDO.setStatus(1); // 失败
            logDO.setErrorMsg(e.getMessage());
            throw e;
        } finally {
            // 记录执行时长
            logDO.setDuration(System.currentTimeMillis() - startTime);
            // 异步保存日志
            adminLogService.recordLog(logDO);
        }

        return result;
    }

    /**
     * 获取请求对象
     */
    private HttpServletRequest getRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

    /**
     * 获取IP地址
     */
    private String getIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多个代理时取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
