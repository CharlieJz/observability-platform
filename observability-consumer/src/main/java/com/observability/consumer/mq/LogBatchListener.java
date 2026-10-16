package com.observability.consumer.mq;

import com.observability.common.dto.LogEntry;
import com.observability.consumer.config.RabbitConfig;
import com.observability.consumer.document.LogDocument;
import com.observability.consumer.repository.LogRepository;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 日志批量消费监听器。
 * <p>
 * 由批量容器攒批（200 条或 1 秒）后一次性投递，本类负责：
 * <ul>
 *   <li>逐条解析消息为 ES 文档（解析失败的单独 NACK 进死信队列，不拖累整批）；</li>
 *   <li>批量写入 ES（saveAll 一次 bulk）；</li>
 *   <li>写入成功 → 批量 ACK（multiple=true），消息从 MQ 删除；</li>
 *   <li>写入失败 → 重试一次；仍失败 → 批量 NACK（requeue=false）进死信队列兜底。</li>
 * </ul>
 * 手动 ACK 的顺序保证"先落库再确认"：ES 宕机期间消息留在 MQ，恢复后自动补写。
 */
@Component
public class LogBatchListener {

    private static final Logger log = LoggerFactory.getLogger(LogBatchListener.class);

    private final LogRepository logRepository;
    private final Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();

    public LogBatchListener(LogRepository logRepository) {
        this.logRepository = logRepository;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE, ackMode = "MANUAL", containerFactory = "batchContainerFactory")
    public void onBatch(List<Message> messages, Channel channel) throws Exception {
        if (messages == null || messages.isEmpty()) {
            return;
        }

        List<LogDocument> docs = new ArrayList<>(messages.size());
        long maxTag = 0L;
        for (Message msg : messages) {
            long tag = msg.getMessageProperties().getDeliveryTag();
            try {
                docs.add(toDocument(msg));
                maxTag = Math.max(maxTag, tag);
            } catch (Exception e) {
                // 单条解析失败：只 NACK 这一条进死信队列，避免毒消息拖死整批
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

    private LogDocument toDocument(Message msg) {
        LogEntry entry = (LogEntry) converter.fromMessage(msg);
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
        return doc;
    }
}
