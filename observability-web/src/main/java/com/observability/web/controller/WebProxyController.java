package com.observability.web.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

/**
 * 聚合代理接口。
 * 前端页面只访问本模块（8085），由这里转发到各业务模块，避免浏览器跨域。
 */
@RestController
@RequestMapping("/api")
public class WebProxyController {

    private static final Logger log = LoggerFactory.getLogger(WebProxyController.class);

    private final RestTemplate restTemplate;

    @Value("${collector.url}")
    private String collectorUrl;

    @Value("${tracing.url}")
    private String tracingUrl;

    @Value("${alert.url}")
    private String alertUrl;

    public WebProxyController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 日志全文检索（转发 8081）。
     * GET /api/logs/search?keyword=&serviceName=
     */
    @GetMapping("/logs/search")
    public String searchLogs(@RequestParam String keyword,
                             @RequestParam(required = false) String serviceName) {
        StringBuilder url = new StringBuilder(collectorUrl)
                .append("/api/logs/search?keyword=")
                .append(keyword);
        if (serviceName != null && !serviceName.isBlank()) {
            url.append("&serviceName=").append(serviceName);
        }
        return getJson(url.toString());
    }

    /**
     * 链路 trace 列表（转发 8083）。
     * GET /api/tracing/traces
     */
    @GetMapping("/tracing/traces")
    public String listTraces() {
        return getJson(tracingUrl + "/api/tracing/traces");
    }

    /**
     * 单条链路详情（转发 8083）。
     * GET /api/tracing/traces/{traceId}
     */
    @GetMapping("/tracing/traces/{traceId}")
    public String getTrace(@PathVariable String traceId) {
        return getJson(tracingUrl + "/api/tracing/traces/" + traceId);
    }

    /**
     * 告警规则列表（转发 8084）。
     * GET /api/alerts/rules
     */
    @GetMapping("/alerts/rules")
    public String listRules() {
        return getJson(alertUrl + "/api/alerts/rules");
    }

    /**
     * 告警记录列表（转发 8084）。
     * GET /api/alerts/records
     */
    @GetMapping("/alerts/records")
    public String listRecords() {
        return getJson(alertUrl + "/api/alerts/records");
    }

    /**
     * 统一转发：调下游模块 REST 接口，原样返回 JSON 字符串。
     * 下游异常时返回错误信息 JSON，前端可提示。
     */
    private String getJson(String url) {
        try {
            String body = restTemplate.getForObject(url, String.class);
            log.info("[WEB-PROXY] GET {} -> 200", url);
            return body != null ? body : "[]";
        } catch (Exception e) {
            log.warn("[WEB-PROXY] GET {} -> {}", url, e.getMessage());
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }
}
