package com.lalal.framework.idempotent;

import java.lang.annotation.*;

/**
 * 幂等性注解
 * 用于标记需要保证幂等性的方法
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    /**
     * 幂等键表达式
     * 支持占位符：
     * - ${paramName} - 方法参数值
     * - ${#paramName} - SpEL表达式
     * - ${header.headerName} - 请求头
     * 留空则使用 类名:方法名:参数哈希
     */
    String key() default "";

    /**
     * 幂等键过期时间（秒）
     * 默认5分钟
     */
    long expire() default 300;

    /**
     * 是否缓存返回结果
     * 如果为true，相同请求将直接返回上次的结果
     * 如果为false，重复请求将抛出异常
     */
    boolean cacheResult() default false;

    /**
     * 提示消息
     * 当检测到重复请求时的提示信息
     */
    String message() default "请勿重复提交";

    /**
     * 是否删除幂等键
     * 如果为true，方法执行完成后立即删除key
     * 如果为false，key会保留直到过期
     * 注意：如果cacheResult=true，则不能立即删除key
     */
    boolean deleteKeyOnSuccess() default false;
}
