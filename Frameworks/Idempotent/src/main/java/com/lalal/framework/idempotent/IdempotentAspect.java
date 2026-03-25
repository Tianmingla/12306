package com.lalal.framework.idempotent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lalal.framework.cache.SafeCacheTemplate;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * 幂等性切面
 * 使用 Redis SET NX EX 实现分布式锁
 */
@Aspect
@Component
public class IdempotentAspect {

    private static final Logger log = LoggerFactory.getLogger(IdempotentAspect.class);

    private static final String IDEMPOTENT_PREFIX = "idempotent:";
    private static final String RESULT_PREFIX = "idempotent:result:";

    @Autowired
    private SafeCacheTemplate safeCacheTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private ObjectMapper objectMapper;

    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();

    @Around("@annotation(com.lalal.framework.idempotent.Idempotent)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Idempotent idempotent = method.getAnnotation(Idempotent.class);

        // 解析幂等键
        String key = buildKey(method, idempotent, joinPoint.getArgs(), joinPoint);
        String fullKey = IDEMPOTENT_PREFIX + key;
        String resultKey = RESULT_PREFIX + key;

        log.debug("Idempotent check for key: {}", fullKey);

        // 如果需要缓存结果，先检查是否有缓存
        if (idempotent.cacheResult()) {
            Object cachedResult = safeCacheTemplate.get(resultKey);
            if (cachedResult != null) {
                log.debug("Return cached result for key: {}", fullKey);
                return cachedResult;
            }
        }

        // 尝试获取分布式锁
        String lockKey = "lock:" + fullKey;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 尝试获取锁，最多等待1秒，锁持有时间为注解定义的过期时间+5秒
            boolean locked = lock.tryLock(1, idempotent.expire() + 5, TimeUnit.SECONDS);

            if (!locked) {
                log.warn("Failed to acquire idempotent lock for key: {}", fullKey);
                throw new IdempotentException(idempotent.message());
            }

            // 双重检查：可能其他线程已经执行完成
            if (idempotent.cacheResult()) {
                Object cachedResult = safeCacheTemplate.get(resultKey);
                if (cachedResult != null) {
                    log.debug("Return cached result after lock for key: {}", fullKey);
                    return cachedResult;
                }
            }

            // 执行目标方法
            Object result = joinPoint.proceed();

            // 如果需要缓存结果，缓存返回值
            if (idempotent.cacheResult()) {
                try {
                    safeCacheTemplate.set(resultKey, result, idempotent.expire(), TimeUnit.SECONDS);
                    log.debug("Cached result for key: {}", resultKey);
                } catch (Exception e) {
                    log.error("Failed to cache result for key: {}", resultKey, e);
                }
            }

            // 如果需要立即删除幂等键（且不缓存结果）
            if (idempotent.deleteKeyOnSuccess() && !idempotent.cacheResult()) {
                safeCacheTemplate.del(fullKey);
                log.debug("Deleted idempotent key after success: {}", fullKey);
            }

            return result;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IdempotentException("幂等性检查被中断", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 构建幂等键
     */
    private String buildKey(Method method, Idempotent idempotent, Object[] args, ProceedingJoinPoint joinPoint) {
        String keyExpression = idempotent.key();

        // 如果没有指定key表达式，使用默认规则
        if (keyExpression == null || keyExpression.isEmpty()) {
            return method.getDeclaringClass().getSimpleName() + ":" +
                   method.getName() + ":" +
                   java.util.Arrays.hashCode(args);
        }

        // 解析表达式中的占位符
        StringBuilder keyBuilder = new StringBuilder();

        int start = 0;
        while (true) {
            int open = keyExpression.indexOf("${", start);
            if (open == -1) {
                keyBuilder.append(keyExpression.substring(start));
                break;
            }

            keyBuilder.append(keyExpression.substring(start, open));
            int close = keyExpression.indexOf("}", open);

            if (close == -1) {
                keyBuilder.append(keyExpression.substring(open));
                break;
            }

            String placeholder = keyExpression.substring(open + 2, close);
            String value = resolvePlaceholder(placeholder, method, args, joinPoint);
            keyBuilder.append(value);
            start = close + 1;
        }

        return keyBuilder.toString();
    }

    /**
     * 解析占位符
     */
    private String resolvePlaceholder(String placeholder, Method method, Object[] args, ProceedingJoinPoint joinPoint) {
        try {
            // Header: ${header.headerName}
            if (placeholder.startsWith("header.")) {
                String headerName = placeholder.substring("header.".length());
                ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attributes != null) {
                    HttpServletRequest request = attributes.getRequest();
                    String headerValue = request.getHeader(headerName);
                    return headerValue != null ? headerValue : "";
                }
                return "";
            }

            // SpEL表达式: ${#paramName.field}
            if (placeholder.startsWith("#")) {
                return parseSpEL(placeholder, method, args);
            }

            // 参数名: ${paramName}
            String[] paramNames = nameDiscoverer.getParameterNames(method);
            if (paramNames != null) {
                for (int i = 0; i < paramNames.length; i++) {
                    if (paramNames[i].equals(placeholder) && i < args.length) {
                        Object arg = args[i];
                        return arg != null ? String.valueOf(arg) : "";
                    }
                }
            }

            return "";
        } catch (Exception e) {
            log.error("Failed to resolve placeholder: {}", placeholder, e);
            return "";
        }
    }

    /**
     * 解析SpEL表达式
     */
    private String parseSpEL(String expression, Method method, Object[] args) {
        try {
            String[] paramNames = nameDiscoverer.getParameterNames(method);

            EvaluationContext context = new StandardEvaluationContext();
            if (paramNames != null) {
                for (int i = 0; i < paramNames.length && i < args.length; i++) {
                    context.setVariable(paramNames[i], args[i]);
                }
            }

            Expression exp = parser.parseExpression(expression);
            Object value = exp.getValue(context);
            return value != null ? String.valueOf(value) : "";
        } catch (Exception e) {
            log.error("Failed to parse SpEL: {}", expression, e);
            return "";
        }
    }

    /**
     * 幂等性异常
     */
    public static class IdempotentException extends RuntimeException {
        public IdempotentException(String message) {
            super(message);
        }

        public IdempotentException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
