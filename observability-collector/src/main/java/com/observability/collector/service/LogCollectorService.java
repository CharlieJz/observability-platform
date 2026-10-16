package com.observability.collector.service;

import com.observability.collector.document.LogDocument;
import com.observability.collector.mq.LogBatchPublisher;
import com.observability.collector.repository.LogRepository;
import com.observability.common.dto.LogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 日志采集处理服务。
 * <p>
 * 上报链路：接口只负责把日志交给批量发布器（削峰），collector 保持轻量、秒回；
 * 日志真正写入 Elasticsearch 由独立的 consumer 模块批量消费完成。
 * 检索能力保留在本服务（web 前端经由本服务查询 ES）。
 * <p>
 * 分级存储：ERROR/WARN 异常日志全量保真（定位错误的核心依据）；
 * INFO/DEBUG 正常日志摘要化——自动抽取 message 中的 key=value 键值对进 tags
 * （结构化保留关键业务信息，可精确检索），message 本体只保留短摘要，
 * 显著减小 MQ 与 ES 的存储压力，同时排障关键信息零丢失。
 */
@Service
public class LogCollectorService {

    private static final Logger log = LoggerFactory.getLogger(LogCollectorService.class);

    /** 摘要消息体长度上限 */
    private static final int SUMMARY_LEN = 150;
    /** 单条日志最多抽取的键值对数量 */
    private static final int MAX_KV = 10;
    /** key=value 键值对：key 字母开头，value 为非空白连续串 */
    private static final Pattern KV_PATTERN = Pattern.compile("([A-Za-z][A-Za-z0-9_.-]{0,32})=([^\\s,;]{1,64})");

    private final LogBatchPublisher logBatchPublisher;
    private final LogRepository logRepository;

    public LogCollectorService(LogBatchPublisher logBatchPublisher, LogRepository logRepository) {
        this.logBatchPublisher = logBatchPublisher;
        this.logRepository = logRepository;
    }

    @Async("logAsyncExecutor")
    public void collect(LogEntry entry) {
        // 分级压缩：异常日志全量，正常日志摘要化（抽取键值对 + 短摘要）
        compact(entry);
        // 削峰：日志进入批量发布器（攒批后一条消息发 MQ），接口不等待 ES 写入
        logBatchPublisher.add(entry);
        log.debug("[LOG-BUFFER] service={} level={} traceId={} -> 批量发布器",
                entry.getServiceName(), entry.getLevel(),
                entry.getTraceId() != null ? entry.getTraceId() : "no-trace");
    }

    /**
     * 分级压缩（摘要提取）：
     * <ul>
     *   <li>ERROR / WARN：全量保留，不动 message / tags（定位错误的依据）；</li>
     *   <li>INFO / DEBUG：从 message 正则抽取 {@code key=value} 键值对合并进 tags（结构化保留关键业务信息），
     *        message 本体截断为短摘要并加 [summary] 标记。</li>
     * </ul>
     */
    private void compact(LogEntry entry) {
        if (entry.getLevel() == null || entry.getMessage() == null) {
            return;
        }
        String level = entry.getLevel().toUpperCase();
        if ("ERROR".equals(level) || "WARN".equals(level)) {
            return;
        }
        String msg = entry.getMessage();

        // 1) 抽取 key=value 键值对 → tags（原有 tags 优先，不覆盖）
        Map<String, String> tags = entry.getTags() != null ? new HashMap<>(entry.getTags()) : new HashMap<>();
        Matcher m = KV_PATTERN.matcher(msg);
        int count = 0;
        while (m.find() && count < MAX_KV) {
            tags.putIfAbsent(m.group(1), m.group(2));
            count++;
        }

        // 2) message 本体 → 短摘要
        String summary = msg.length() > SUMMARY_LEN
                ? msg.substring(0, SUMMARY_LEN) + "...[summary]"
                : msg;
        entry.setMessage(summary);
        entry.setTags(tags);
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
