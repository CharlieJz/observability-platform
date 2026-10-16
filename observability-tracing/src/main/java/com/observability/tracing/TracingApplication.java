package com.observability.tracing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.client.RestTemplate;

/**
 * 分布式链路追踪服务启动类。
 */
@SpringBootApplication
@EnableAsync
public class TracingApplication {

    public static void main(String[] args) {
        SpringApplication.run(TracingApplication.class, args);
    }

    /** 用于转发 span 给 Zipkin 的 HTTP 客户端 */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
