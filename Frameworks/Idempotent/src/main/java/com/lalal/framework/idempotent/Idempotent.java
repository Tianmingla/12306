package com.lalal.framework.idempotent;

import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {
    String key() default ""; // 幂等键表达式
    long expire() default 5 * 60; // 幂等键过期时间（秒）
    // 预留：类型（API/MQ）
}
