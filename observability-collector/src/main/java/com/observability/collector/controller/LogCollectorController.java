package com.observability.collector.controller;

import com.observability.collector.document.LogDocument;
import com.observability.collector.dto.LogEntry;
import com.observability.collector.service.LogCollectorService;
import com.observability.common.model.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 日志采集接口。
 * 各微服务通过该接口上报日志，采集服务异步处理；支持全文检索查询。
 */
@RestController
@RequestMapping("/api/logs")
public class LogCollectorController {

    private final LogCollectorService logCollectorService;

    public LogCollectorController(LogCollectorService logCollectorService) {
        this.logCollectorService = logCollectorService;
    }

    /**
     * 上报单条日志。
     * POST /api/logs
     */
    @PostMapping
    public ApiResponse<Void> collect(@Valid @RequestBody LogEntry entry) {
        logCollectorService.collect(entry);
        return ApiResponse.ok(null);
    }

    /**
     * 批量上报日志。
     * POST /api/logs/batch
     */
    @PostMapping("/batch")
    public ApiResponse<Integer> collectBatch(@Valid @RequestBody List<@Valid LogEntry> entries) {
        entries.forEach(logCollectorService::collect);
        return ApiResponse.ok(entries.size());
    }

    /**
     * 全文检索日志。
     * GET /api/logs/search?keyword=订单&serviceName=order-service
     * keyword 必填；serviceName 可选过滤。
     */
    @GetMapping("/search")
    public ApiResponse<List<LogDocument>> search(
            @RequestParam String keyword,
            @RequestParam(required = false) String serviceName) {
        return ApiResponse.ok(logCollectorService.search(keyword, serviceName));
    }
}
