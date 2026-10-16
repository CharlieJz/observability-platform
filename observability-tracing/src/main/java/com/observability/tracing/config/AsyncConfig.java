package com.observability.tracing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步线程池配置：用于异步转发 span 给 Zipkin，不阻塞上报接口。
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "traceAsyncExecutor")
    public Executor traceAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("trace-async-");
        executor.initialize();
        return executor;
    }
}
