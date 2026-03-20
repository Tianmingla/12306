package com.lalal.framework.idempotent;

import com.lalal.framework.cache.SafeCacheTemplate;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

@Aspect
@Component
@Order(1)
public class IdempotentAspect {
    @Autowired
    private SafeCacheTemplate safeCacheTemplate;

    @Around("@annotation(com.lalal.framework.idempotent.Idempotent)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Idempotent idempotent = method.getAnnotation(Idempotent.class);
        String key = idempotent.key();
        if (key.isEmpty()) {
            key = method.getDeclaringClass().getName() + ":" + method.getName() + ":" + joinPoint.getArgs().hashCode();
        }
        //TODO
//        boolean success = Boolean.TRUE.equals(safeCacheTemplate.set(key, "1", idempotent.expire(), TimeUnit.SECONDS));
        boolean success=true;
        if (!success) {
            throw new RuntimeException("重复请求，请勿重复提交");
        }
        try {
            return joinPoint.proceed();
        } finally {
            // 可选：不立即删除key，防止并发
        }
    }
}
