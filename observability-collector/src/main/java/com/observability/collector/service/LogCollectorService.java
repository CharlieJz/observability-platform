package com.observability.collector.service;

import com.observability.collector.document.LogDocument;
import com.observability.collector.dto.LogEntry;
import com.observability.collector.repository.LogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 日志采集处理服务。
 * 异步消费上报的日志并写入 Elasticsearch，支持后续全文检索。
 */
@Service
public class LogCollectorService {

    private static final Logger log = LoggerFactory.getLogger(LogCollectorService.class);

    private final LogRepository logRepository;

    public LogCollectorService(LogRepository logRepository) {
        this.logRepository = logRepository;
    }

    @Async("logAsyncExecutor")
    public void collect(LogEntry entry) {
        long ts = entry.getTimestamp() != null ? entry.getTimestamp() : System.currentTimeMillis();

        // 组装 ES 文档：一条日志 = 一个文档
        LogDocument doc = new LogDocument();
        // 主键：traceId + 时间戳 + 随机后缀，保证唯一，避免重复上报覆盖
        String traceId = entry.getTraceId() != null ? entry.getTraceId() : "no-trace";
        doc.setId(traceId + "-" + ts + "-" + UUID.randomUUID().toString().substring(0, 8));
        doc.setServiceName(entry.getServiceName());
        doc.setLevel(entry.getLevel());
        doc.setMessage(entry.getMessage());
        doc.setTraceId(traceId);
        doc.setTimestamp(ts);
        doc.setTags(entry.getTags());

        // 写入 ES（批量场景下这里会升级为批量写入）
        logRepository.save(doc);

        log.info("[LOG-STORE] service={} level={} time={} traceId={} -> ES index=logs",
                entry.getServiceName(), entry.getLevel(), Instant.ofEpochMilli(ts), traceId);
    }

    /**
     * 全文检索日志：按关键词搜索 message 字段。
     * 可选按服务名过滤；返回按时间倒序（最近优先）。
     */
    public List<LogDocument> search(String keyword, String serviceName) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        List<LogDocument> result;
        if (serviceName != null && !serviceName.isBlank()) {
            result = logRepository.searchByKeywordAndService(keyword.trim(), serviceName.trim());
        } else {
            result = logRepository.searchByKeyword(keyword.trim());
        }
        // 按时间倒序（ES 返回默认按相关度，这里统一按时间排，最近优先）
        result.sort((a, b) -> Long.compare(
                b.getTimestamp() == null ? 0L : b.getTimestamp(),
                a.getTimestamp() == null ? 0L : a.getTimestamp()));
        log.info("[LOG-SEARCH] keyword={} service={} hits={}", keyword, serviceName, result.size());
        return result;
    }
}
