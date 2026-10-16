package com.observability.collector.service;

import com.observability.collector.document.LogDocument;
import com.observability.collector.mq.LogMessagePublisher;
import com.observability.collector.repository.LogRepository;
import com.observability.common.dto.LogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 日志采集处理服务。
 * <p>
 * 上报链路：接口只负责把日志发布到 RabbitMQ（削峰），collector 保持轻量、秒回；
 * 日志真正写入 Elasticsearch 由独立的 consumer 模块批量消费完成。
 * 检索能力保留在本服务（web 前端经由本服务查询 ES）。
 */
@Service
public class LogCollectorService {

    private static final Logger log = LoggerFactory.getLogger(LogCollectorService.class);

    private final LogMessagePublisher logMessagePublisher;
    private final LogRepository logRepository;

    public LogCollectorService(LogMessagePublisher logMessagePublisher, LogRepository logRepository) {
        this.logMessagePublisher = logMessagePublisher;
        this.logRepository = logRepository;
    }

    @Async("logAsyncExecutor")
    public void collect(LogEntry entry) {
        // 削峰：日志只进 MQ，由 consumer 批量写 ES，接口不等待 ES 写入
        logMessagePublisher.publish(entry);
        log.info("[LOG-PUBLISH] service={} level={} traceId={} -> MQ queue={}",
                entry.getServiceName(), entry.getLevel(),
                entry.getTraceId() != null ? entry.getTraceId() : "no-trace", "log.queue");
    }

    /**
     * 全文检索日志：按关键词搜索 message 与 traceId 字段。
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
