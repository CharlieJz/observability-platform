package com.observability.collector.service;

import com.observability.collector.dto.LogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * 日志采集处理服务。
 * 异步消费上报的日志：第一版先做结构化落盘记录（便于观察效果），
 * 后续接入 Elasticsearch 等存储时，在此处替换为写入存储实现。
 */
@Service
public class LogCollectorService {

    private static final Logger log = LoggerFactory.getLogger(LogCollectorService.class);

    @Async("logAsyncExecutor")
    public void collect(LogEntry entry) {
        long ts = entry.getTimestamp() != null ? entry.getTimestamp() : System.currentTimeMillis();
        // 结构化输出：服务名 | 级别 | 时间 | traceId | 内容 | 标签
        log.info("[LOG-COLLECTED] service={} level={} time={} traceId={} tags={} message={}",
                entry.getServiceName(),
                entry.getLevel(),
                Instant.ofEpochMilli(ts),
                entry.getTraceId() != null ? entry.getTraceId() : "-",
                entry.getTags() != null ? entry.getTags() : "{}",
                entry.getMessage());
        // TODO(下一阶段): 接入存储，如 logStore.save(entry)
    }
}
