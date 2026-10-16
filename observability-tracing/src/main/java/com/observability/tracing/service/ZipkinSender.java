package com.observability.tracing.service;

import com.observability.tracing.dto.SpanEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Zipkin 上报组件（独立 Bean，保证 @Async 代理生效）。
 * 将 Span 以 Zipkin v2 JSON 格式异步提交给 Zipkin /api/v2/spans。
 */
@Component
public class ZipkinSender {

    private static final Logger log = LoggerFactory.getLogger(ZipkinSender.class);

    private final RestTemplate restTemplate;

    /** Zipkin 服务器地址（虚拟机） */
    @Value("${zipkin.base-url:http://192.168.153.100:9411}")
    private String zipkinBaseUrl;

    public ZipkinSender(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 异步提交单条 span 给 Zipkin。
     * Zipkin 标准格式：traceId/id/parentId/name/timestamp/duration/localEndpoint。
     */
    @Async("traceAsyncExecutor")
    public void send(SpanEntry entry) {
        try {
            Map<String, Object> zipkinSpan = new LinkedHashMap<>();
            zipkinSpan.put("traceId", entry.getTraceId());
            zipkinSpan.put("id", entry.getSpanId());
            if (entry.getParentId() != null && !entry.getParentId().isBlank()) {
                zipkinSpan.put("parentId", entry.getParentId());
            }
            zipkinSpan.put("name", entry.getName());
            if (entry.getTimestamp() != null) {
                zipkinSpan.put("timestamp", entry.getTimestamp());
            }
            if (entry.getDuration() != null) {
                zipkinSpan.put("duration", entry.getDuration());
            }
            // 上报者即本段的服务端点
            Map<String, Object> endpoint = new LinkedHashMap<>();
            endpoint.put("serviceName", entry.getServiceName());
            zipkinSpan.put("localEndpoint", endpoint);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<List<Map<String, Object>>> request =
                    new HttpEntity<>(List.of(zipkinSpan), headers);

            ResponseEntity<String> resp = restTemplate.postForEntity(
                    zipkinBaseUrl + "/api/v2/spans", request, String.class);
            log.info("[TRACE-ZIPKIN] span={} -> zipkin status={}", entry.getSpanId(), resp.getStatusCode());
        } catch (Exception e) {
            // Zipkin 不可达时不影响上报接口，记录即可
            log.warn("[TRACE-ZIPKIN] 转发Zipkin失败 span={} err={}", entry.getSpanId(), e.getMessage());
        }
    }
}
