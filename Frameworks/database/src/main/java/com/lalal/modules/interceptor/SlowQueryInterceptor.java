package com.lalal.modules.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.util.Properties;

/**
 * MyBatis 慢查询拦截器
 * 拦截执行时间超过阈值的 SQL 语句并记录日志
 */
@Slf4j
@Intercepts({
    @Signature(
        type = Executor.class,
        method = "query",
        args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}
    ),
    @Signature(
        type = Executor.class,
        method = "update",
        args = {MappedStatement.class, Object.class}
    )
})
public class SlowQueryInterceptor implements Interceptor {

    /**
     * 慢查询阈值（毫秒），默认 1000ms
     */
    private static final long SLOW_QUERY_THRESHOLD_MS = 1000;

    /**
     * 是否启用慢查询日志
     */
    private boolean enabled = true;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if (!enabled) {
            return invocation.proceed();
        }

        long startTime = System.currentTimeMillis();
        Object result = invocation.proceed();
        long costTime = System.currentTimeMillis() - startTime;

        if (costTime > SLOW_QUERY_THRESHOLD_MS) {
            MappedStatement mappedStatement = (MappedStatement) invocation.getArgs()[0];
            Object parameter = invocation.getArgs()[1];
            SqlCommandType sqlCommandType = mappedStatement.getSqlCommandType();

            log.warn("[SLOW QUERY] {}ms | Type: {} | Statement: {} | Parameter: {}",
                    costTime,
                    sqlCommandType,
                    mappedStatement.getId(),
                    parameter != null ? parameter.toString() : "null"
            );

            // TODO: 可以集成到 Prometheus 指标
            // Metrics.counter("slow_query_total",
            //     "statement", mappedStatement.getId(),
            //     "type", sqlCommandType.name()
            // ).increment();
        }

        return result;
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        String enabledStr = properties.getProperty("enabled");
        if (enabledStr != null) {
            this.enabled = Boolean.parseBoolean(enabledStr);
        }
    }
}
