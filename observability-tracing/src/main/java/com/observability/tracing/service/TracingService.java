package com.observability.tracing.service;

import com.observability.tracing.dto.SpanEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 链路追踪服务。
 * 接收各微服务上报的 Span，并异步转发给 Zipkin 存储展示；
 * 同时在本地内存留一份，供 Trace 查询接口使用。
 */
@Service
public class TracingService {

    private static final Logger log = LoggerFactory.getLogger(TracingService.class);

    private final ZipkinSender zipkinSender;

    /** 本地内存链路存储：traceId -> 该链路所有 span */
    private final Map<String, List<Map<String, Object>>> traceStore = new ConcurrentHashMap<>();

    public TracingService(ZipkinSender zipkinSender) {
        this.zipkinSender = zipkinSender;
    }

    /**
     * 接收一条 span：存入本地内存 + 异步转发给 Zipkin。
     */
    public void accept(SpanEntry entry) {
        // 1. 本地内存记录（供查询接口）
        Map<String, Object> spanMap = new LinkedHashMap<>();
        spanMap.put("traceId", entry.getTraceId());
        spanMap.put("spanId", entry.getSpanId());
        spanMap.put("parentId", entry.getParentId());
        spanMap.put("name", entry.getName());
        spanMap.put("serviceName", entry.getServiceName());
        spanMap.put("timestamp", entry.getTimestamp());
        spanMap.put("duration", entry.getDuration());
        spanMap.put("kind", entry.getKind());
        traceStore.computeIfAbsent(entry.getTraceId(), k -> new ArrayList<>()).add(spanMap);

        // 2. 异步转发 Zipkin（独立 Bean，@Async 代理生效）
        zipkinSender.send(entry);

        log.info("[TRACE-RECEIVED] traceId={} span={} parent={} service={} name={} duration={}us",
                entry.getTraceId(), entry.getSpanId(), entry.getParentId(),
                entry.getServiceName(), entry.getName(), entry.getDuration());
    }

    /**
     * 按 traceId 查询整条链路的全部 span。
     */
    public List<Map<String, Object>> getTrace(String traceId) {
        return traceStore.getOrDefault(traceId, List.of());
    }

    /**
     * 查询所有已记录的 traceId（用于列表展示）。
     */
    public List<String> listTraceIds() {
        return new ArrayList<>(traceStore.keySet());
    }

    /**
     * 模拟一次跨服务调用链：gateway → order → inventory。
     * 生成 3 个 span（1 个根 span + 2 个子 span），用同一 traceId 串联。
     * 返回生成的 traceId，供 Zipkin/查询接口查看。
     */
    public String simulateChain() {
        String traceId = randomHex(32);
        long base = System.currentTimeMillis() * 1000; // 转微秒
        String spanGateway = randomHex(16);
        String spanOrder = randomHex(16);
        String spanInventory = randomHex(16);

        // Span1：根 span —— gateway 接收外部请求
        accept(buildSpan(traceId, spanGateway, null, "gateway",
                "GET /api/order", base, 1200000L, "SERVER"));

        // Span2：gateway 调用 order —— 子 span
        accept(buildSpan(traceId, spanOrder, spanGateway, "order-service",
                "call-order-service", base + 100000L, 800000L, "CLIENT"));

        // Span3：order 调用 inventory —— 孙 span
        accept(buildSpan(traceId, spanInventory, spanOrder, "inventory-service",
                "call-inventory-service", base + 300000L, 500000L, "CLIENT"));

        return traceId;
    }

    private SpanEntry buildSpan(String traceId, String spanId, String parentId,
                                String serviceName, String name, long timestamp, long duration, String kind) {
        SpanEntry span = new SpanEntry();
        span.setTraceId(traceId);
        span.setSpanId(spanId);
        span.setParentId(parentId);
        span.setServiceName(serviceName);
        span.setName(name);
        span.setTimestamp(timestamp);
        span.setDuration(duration);
        span.setKind(kind);
        return span;
    }

    /** 生成指定长度的十六进制随机串（Zipkin traceId 规范：小写十六进制） */
    private String randomHex(int length) {
        String hex = "0123456789abcdef";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(hex.charAt((int) (Math.random() * 16)));
        }
        return sb.toString();
    }
}
