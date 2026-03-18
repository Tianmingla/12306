package com.lalal.modules.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * 流量监控过滤器，记录流量并标记当前是否为高峰
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TrafficMonitorFilter implements GlobalFilter, Ordered {

    private final ReactiveStringRedisTemplate redisTemplate;

    @Value("${traffic.peak-threshold:100}")
    private int peakThreshold;

    private static final String TRAFFIC_COUNT_KEY = "traffic:count:";
    private static final String PEAK_STATUS_KEY = "traffic:peak:status";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long currentSecond = System.currentTimeMillis() / 1000;
        String key = TRAFFIC_COUNT_KEY + currentSecond;

        return redisTemplate.opsForValue().increment(key)
                .flatMap(count -> {
                    if (count == 1) {
                        // 设置过期时间，防止Redis内存溢出
                        redisTemplate.expire(key, Duration.ofSeconds(10)).subscribe();
                    }
                    
                    if (count > peakThreshold) {
                        // 标记为高峰状态
                        return redisTemplate.opsForValue().set(PEAK_STATUS_KEY, "true", Duration.ofSeconds(5))
                                .then(chain.filter(exchange));
                    }
                    return chain.filter(exchange);
                });
    }

    @Override
    public int getOrder() {
        return -1; // 优先级最高
    }
}
