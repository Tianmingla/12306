package com.lalal.modules.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.lalal.modules.handler.MyMetaObjectHandler;
import com.lalal.modules.interceptor.SlowQueryInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusAutoConfiguration {
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(){
        MybatisPlusInterceptor mybatisPlusInterceptor=new MybatisPlusInterceptor();
        mybatisPlusInterceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return mybatisPlusInterceptor;
    }

    @Bean
    public MetaObjectHandler MyMetaObjectHandler(){
        return new MyMetaObjectHandler();
    }

    /**
     * 慢查询拦截器
     * 可通过配置 mybatis.slow-query.enabled=false 禁用
     */
    @Bean
    @ConditionalOnProperty(name = "mybatis.slow-query.enabled", havingValue = "true", matchIfMissing = true)
    public SlowQueryInterceptor slowQueryInterceptor() {
        return new SlowQueryInterceptor();
    }
}
