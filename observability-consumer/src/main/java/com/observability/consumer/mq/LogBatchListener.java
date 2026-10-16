package com.observability.consumer.mq;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.observability.common.dto.LogEntry;
import com.observability.consumer.config.RabbitConfig;
import com.observability.consumer.document.LogDocument;
import com.observability.consumer.repository.LogRepository;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 日志批量消费监听器。
 * <p>
 * 生产者按批发布（一条消息 = 最多 100 条日志的 JSON 数组），
 * 本类收到批量容器投递的一批消息后负责：
 * <ul>
 *   <li>拆包解析：每条消息解析出 List&lt;LogEntry&gt;，逐条转为 ES 文档（解析失败的单独 NACK 进死信队列）；</li>
 *   <li>批量写入 ES（saveAll 一次 bulk）；</li>
 *   <li>写入成功 → 批量 ACK（multiple=true），消息从 MQ 删除；</li>
 *   <li>写入失败 → 重试一次；仍失败 → 批量 NACK（requeue=false）进死信队列兜底。</li>
 * </ul>
 * 手动 ACK 的顺序保证"先落库再确认"：ES 宕机期间消息留在 MQ，恢复后自动补写。
 * <p>
 * 注意：body 为 JSON 数组，直接用 ObjectMapper + TypeReference 反序列化，
 * 不依赖 Spring AMQP 类型头（List 泛型在类型头中不可靠）。
 */
@Component
public class LogBatchListener {

    private static final Logger log = LoggerFactory.getLogger(LogBatchListener.class);

    private static final TypeReference<List<LogEntry>> LIST_TYPE = new TypeReference<>() {
    };

    private final LogRepository logRepository;
    private final ObjectMapper objectMapper;

    public LogBatchListener(LogRepository logRepository, ObjectMapper objectMapper) {
        this.logRepository = logRepository;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE, ackMode = "MANUAL", containerFactory = "batchContainerFactory")
    public void onBatch(List<Message> messages, Channel channel) throws Exception {
        if (messages == null || messages.isEmpty()) {
            return;
        }

        List<LogDocument> docs = new ArrayList<>(messages.size() * 8);
        long maxTag = 0L;
        for (Message msg : messages) {
            long tag = msg.getMessageProperties().getDeliveryTag();
            try {
                docs.addAll(toDocuments(msg));
                maxTag = Math.max(maxTag, tag);
            } catch (Exception e) {
                // 单条消息解析失败：只 NACK 这一条进死信队列，避免毒消息拖死整批
                channel.basicNack(tag, false, false);
                log.error("[MSG-PARSE-FAIL] tag={} 进死信队列 log.dlq | err={}", tag, e.getMessage());
            }
        }

        if (docs.isEmpty()) {
            return;
        }

        try {
            logRepository.saveAll(docs);
            channel.basicAck(maxTag, true);
            log.info("[ES-WRITE] batch={} -> ES index=logs, ack tags <= {}", docs.size(), maxTag);
        } catch (Exception e) {
            // 写 ES 失败：重试一次，应对瞬时抖动
            try {
                Thread.sleep(500);
                logRepository.saveAll(docs);
                channel.basicAck(maxTag, true);
                log.info("[ES-WRITE-RETRY-OK] batch={} -> ES index=logs, ack tags <= {}", docs.size(), maxTag);
            } catch (Exception retryEx) {
                // 仍失败：整批不 requeue，进死信队列兜底（ES 恢复后可人工/定时重投）
                channel.basicNack(maxTag, true, false);
                log.error("[ES-WRITE-FAIL] batch={} 进死信队列 log.dlq | err={}", docs.size(), retryEx.getMessage());
            }
        }
    }

    /** 拆包：一条消息（JSON 数组） → 多个 ES 文档 */
    private List<LogDocument> toDocuments(Message msg) throws Exception {
        List<LogEntry> entries = objectMapper.readValue(msg.getBody(), LIST_TYPE);
        List<LogDocument> docs = new ArrayList<>(entries.size());
        for (LogEntry entry : entries) {
            long ts = entry.getTimestamp() != null ? entry.getTimestamp() : System.currentTimeMillis();
            String traceId = entry.getTraceId() != null ? entry.getTraceId() : "no-trace";
            LogDocument doc = new LogDocument();
            doc.setId(traceId + "-" + ts + "-" + UUID.randomUUID().toString().substring(0, 8));
            doc.setServiceName(entry.getServiceName());
            doc.setLevel(entry.getLevel());
            doc.setMessage(entry.getMessage());
            doc.setTraceId(traceId);
            doc.setTimestamp(ts);
            doc.setTags(entry.getTags());
            docs.add(doc);
        }
        return docs;
    }
}
