package com.lalal.framework.log;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class LogMonitorAspect {
    private static final Logger logger = LoggerFactory.getLogger(LogMonitorAspect.class);

    @Around("@annotation(com.lalal.framework.log.LogMonitor)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String method = signature.getDeclaringTypeName() + "." + signature.getName();
        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long cost = System.currentTimeMillis() - start;
            logger.info("[LogMonitor] {} | args={} | result={} | cost={}ms", method, joinPoint.getArgs(), result, cost);
            return result;
        } catch (Throwable e) {
            logger.error("[LogMonitor] {} | args={} | error={}", method, joinPoint.getArgs(), e.getMessage(), e);
            throw e;
        }
    }
}
