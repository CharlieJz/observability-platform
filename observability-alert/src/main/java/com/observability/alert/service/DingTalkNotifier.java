package com.observability.alert.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 钉钉机器人通知组件。
 * 通过钉钉群自定义机器人 webhook 发送告警消息。
 */
@Component
public class DingTalkNotifier {

    private static final Logger log = LoggerFactory.getLogger(DingTalkNotifier.class);

    private final RestTemplate restTemplate;

    /** 钉钉群机器人 webhook 地址（空则不发送） */
    @Value("${dingtalk.webhook:}")
    private String webhook;

    public DingTalkNotifier(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 发送一条告警消息。
     * 返回 true 表示发送成功（或未配置 webhook，视为跳过）。
     */
    public boolean sendAlert(String ruleName, String metricName, Double actualValue, Double threshold) {
        if (webhook == null || webhook.isBlank()) {
            log.info("[DINGTALK] 未配置 webhook，跳过通知");
            return false;
        }
        try {
            String text = String.format(
                    "【告警触发】%s\n指标: %s\n当前值: %s | 阈值: %s\n时间: %s",
                    ruleName, metricName, actualValue, threshold,
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

            Map<String, Object> content = new LinkedHashMap<>();
            content.put("msgtype", "text");
            content.put("text", Map.of("content", text));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(content, headers);

            ResponseEntity<String> resp = restTemplate.postForEntity(webhook, request, String.class);
            boolean ok = resp.getStatusCode().is2xxSuccessful();
            log.info("[DINGTALK] 发送结果 status={} body={}", resp.getStatusCode(), resp.getBody());
            return ok;
        } catch (Exception e) {
            log.warn("[DINGTALK] 发送失败: {}", e.getMessage());
            return false;
        }
    }
}
