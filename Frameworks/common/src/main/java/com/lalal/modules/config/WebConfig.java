package com.lalal.modules.config;

import com.lalal.modules.component.UUIDGenerator;
import com.lalal.modules.filter.RequestIdFilter;
import jakarta.servlet.Filter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
public class WebConfig {
    @Bean
    Filter filter(){
        return new RequestIdFilter(new UUIDGenerator());
    }
}
