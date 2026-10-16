package com.observability.alert;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

/**
 * 告警管理服务启动类。
 */
@SpringBootApplication
@EnableScheduling
public class AlertApplication {

    public static void main(String[] args) {
        SpringApplication.run(AlertApplication.class, args);
    }

    /** 用于查询 Prometheus 指标和调用钉钉 webhook */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
