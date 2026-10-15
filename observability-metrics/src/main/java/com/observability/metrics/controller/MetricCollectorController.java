package com.observability.metrics.controller;

import com.observability.common.model.ApiResponse;
import com.observability.metrics.dto.MetricEntry;
import com.observability.metrics.service.MetricService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 指标监控接口。
 * 接收各微服务上报的业务指标，并暴露查询接口。
 */
@RestController
@RequestMapping("/api/metrics")
public class MetricCollectorController {

    private final MetricService metricService;

    public MetricCollectorController(MetricService metricService) {
        this.metricService = metricService;
    }

    /**
     * 上报一条业务指标。
     * POST /api/metrics
     */
    @PostMapping
    public ApiResponse<Void> report(@Valid @RequestBody MetricEntry entry) {
        metricService.record(entry);
        return ApiResponse.ok(null);
    }

    /**
     * 查询当前已上报的指标台账。
     * GET /api/metrics
     */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.ok(metricService.listAll());
    }
}
