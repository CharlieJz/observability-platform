package com.observability.collector.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步线程池配置。
 * 日志采集入口高频上报，使用独立线程池异步发布到 RabbitMQ，
 * 让接口快速返回，避免阻塞调用方。
 * <p>
 * 保持与原直写 ES 链路一致的线程池参数（2/8/1000），
 * 保证压测对比口径统一：吞吐提升仅来自批量发布，而非线程池扩容。
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    @Bean("logAsyncExecutor")
    public Executor logAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数：日常日志量不大，2 即可
        executor.setCorePoolSize(2);
        // 最大线程数：突发流量时可扩张到 8
        executor.setMaxPoolSize(8);
        // 队列容量：缓冲积压日志，避免直接打满线程
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("log-collect-");
        // 拒绝策略：队列满时由调用线程执行，保证日志不丢
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
