package com.observability.tracing.controller;

import com.observability.common.model.ApiResponse;
import com.observability.tracing.dto.SpanEntry;
import com.observability.tracing.service.TracingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 链路追踪接口：
 * POST /api/tracing/spans          接收微服务上报的 span
 * GET  /api/tracing/traces/{traceId} 查询整条调用链
 * GET  /api/tracing/traces         列出所有 traceId
 */
@RestController
@RequestMapping("/api/tracing")
public class TracingController {

    private final TracingService tracingService;

    public TracingController(TracingService tracingService) {
        this.tracingService = tracingService;
    }

    /** 微服务上报一段调用（span） */
    @PostMapping("/spans")
    public ApiResponse<Void> reportSpan(@Valid @RequestBody SpanEntry span) {
        tracingService.accept(span);
        return ApiResponse.ok(null);
    }

    /** 按 traceId 查询整条链 */
    @GetMapping("/traces/{traceId}")
    public ApiResponse<List<Map<String, Object>>> getTrace(@PathVariable String traceId) {
        return ApiResponse.ok(tracingService.getTrace(traceId));
    }

    /** 列出所有已记录的 traceId */
    @GetMapping("/traces")
    public ApiResponse<List<String>> listTraces() {
        return ApiResponse.ok(tracingService.listTraceIds());
    }

    /** 模拟一次跨服务调用链（gateway → order → inventory），返回生成的 traceId */
    @PostMapping("/simulate")
    public ApiResponse<Map<String, String>> simulate() {
        String traceId = tracingService.simulateChain();
        return ApiResponse.ok(Map.of("traceId", traceId, "message", "已生成3段调用链"));
    }
}
