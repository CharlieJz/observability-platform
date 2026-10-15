package com.observability.metrics.service;

import com.observability.metrics.dto.MetricEntry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 指标处理服务。
 * 使用 Micrometer 的 MeterRegistry 将上报的业务指标注册为
 * Counter/Gauge/Timer，并统一暴露给 Prometheus 抓取。
 */
@Service
public class MetricService {

    private static final Logger log = LoggerFactory.getLogger(MetricService.class);

    private final MeterRegistry meterRegistry;

    /** 内存指标台账：metricKey -> 最近上报值，供 /api/metrics 查询展示 */
    private final Map<String, Double> metricLedger = new ConcurrentHashMap<>();

    public MetricService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * 记录一条业务指标。
     * counter：累加值；gauge：设置当前值；timer：记录耗时。
     */
    public void record(MetricEntry entry) {
        Tags tags = buildTags(entry.getServiceName(), entry.getTags());
        String key = entry.getMetricName() + entry.getServiceName();

        switch (entry.getMetricType()) {
            case "counter" -> {
                Counter counter = meterRegistry.counter(entry.getMetricName(), tags);
                counter.increment(entry.getValue());
                metricLedger.merge(key, entry.getValue(), Double::sum);
            }
            case "gauge" -> {
                Gauge.builder(entry.getMetricName(), () -> entry.getValue())
                        .tags(tags)
                        .register(meterRegistry);
                metricLedger.put(key, entry.getValue());
            }
            case "timer" -> {
                // timer 语义：上报单次耗时（毫秒），记录到计时器直方图
                io.micrometer.core.instrument.Timer.builder(entry.getMetricName())
                        .tags(tags)
                        .publishPercentiles(0.5, 0.9, 0.99)
                        .register(meterRegistry)
                        .record(java.time.Duration.ofMillis(entry.getValue().longValue()));
                metricLedger.put(key, entry.getValue());
            }
            default -> throw new IllegalArgumentException("不支持的指标类型: " + entry.getMetricType());
        }

        log.info("[METRIC-RECORDED] type={} metric={} service={} value={} time={}",
                entry.getMetricType(), entry.getMetricName(), entry.getServiceName(),
                entry.getValue(), Instant.now());
    }

    /**
     * 查询当前指标台账（所有已上报指标的名称、类型聚合值）。
     */
    public List<Map<String, Object>> listAll() {
        List<Map<String, Object>> result = new ArrayList<>();
        metricLedger.forEach((key, value) -> result.add(Map.of(
                "metricKey", key,
                "value", value
        )));
        return result;
    }

    private Tags buildTags(String serviceName, Map<String, String> tags) {
        Tags result = Tags.of("service", serviceName);
        if (tags != null) {
            for (Map.Entry<String, String> e : tags.entrySet()) {
                result = result.and(e.getKey(), e.getValue());
            }
        }
        return result;
    }
}
