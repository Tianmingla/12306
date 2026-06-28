package com.lalal.modules.filter;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
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
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;

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

    @Value("${traffic.monitor-interval}")
    private Long period;

    @Value("${traffic.peak-last}")
    private Long peakLasting;

    @Value("${traffic.sync-interval}")
    private Long syncInterval;

    private static final long MAX_LOCAL_BACKLOG = 100_000_000L;

    private static final String TRAFFIC_COUNT_KEY = "traffic:count:";
    private static final String PEAK_STATUS_KEY = "traffic:peak:status";

    public LongAdder localCounter=new LongAdder(); // 分散各个线程的cas压力 极致性能

    private ScheduledExecutorService syncExecutor;
    @PostConstruct
    public void init() {
        // 自定义线程名，方便排查问题
        syncExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "traffic-sync-thread");
            t.setDaemon(true); // 设置为守护线程
            return t;
        });
        syncExecutor.scheduleAtFixedRate(this::syncToRedis, 1000, syncInterval, TimeUnit.MILLISECONDS);
    }
    @PreDestroy
    public void destroy() {
        if (syncExecutor != null) {
            syncExecutor.shutdown();
        }
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        localCounter.increment();
        return chain.filter(exchange);
    }

    private void syncToRedis() {
        long delta = localCounter.sumThenReset();

        if (delta > 0) {
            long currentPeriod = System.currentTimeMillis() / period;
            String key = TRAFFIC_COUNT_KEY + currentPeriod;

            // 3. 异步写入 Redis，不阻塞定时线程
            redisTemplate.opsForValue()
                    .increment(key,delta)
                    .flatMap(count -> {
                        Mono<Void> pipeline = Mono.empty();
                        if (count == 1) {
                            // 设置过期时间，防止Redis内存溢出
                            pipeline = pipeline.then(
                                    redisTemplate.expire(key, Duration.ofMillis(period))
                                            .then() // expire 返回 Mono<Boolean>，用 then() 转为 Mono<Void>
                            );
                        }

                        if (count > peakThreshold) {
                            // 标记为高峰状态
                            pipeline = pipeline.then(
                                    redisTemplate.opsForValue()
                                            .set(PEAK_STATUS_KEY, "true", Duration.ofMillis(peakLasting))
                                            .then() // set 返回 Mono<Boolean>，转为 Mono<Void>
                            );
                        }
                        return pipeline;
                    })
                    .onErrorResume(e -> {
                        log.error("Redis 流量统计写入失败, key: {}", key, e);
                        // 只有当本地积压量未超过安全阈值时，才将数据加回本地
                        if (localCounter.sum() < MAX_LOCAL_BACKLOG) {
                            localCounter.add(delta);
                        } else {
                            log.warn("本地流量积压已达上限，丢弃本次增量: {}", delta);
                        }
                        return Mono.empty(); // 吞掉异常，防止定时任务中断
                    })
                    .subscribe();
        }
    }

    @Override
    public int getOrder() {
        return -1; // 优先级最高
    }
}
