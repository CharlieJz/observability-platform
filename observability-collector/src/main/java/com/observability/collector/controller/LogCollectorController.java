package com.observability.collector.controller;

import com.observability.collector.dto.LogEntry;
import com.observability.collector.service.LogCollectorService;
import com.observability.common.model.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 日志采集接口。
 * 各微服务通过该接口上报日志，采集服务异步处理。
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
}
