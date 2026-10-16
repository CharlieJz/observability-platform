package com.observability.collector.mq;

import com.observability.collector.config.RabbitConfig;
import com.observability.common.dto.LogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 日志批量发布器。
 * <p>
 * 应用层攒批：攒够 100 条或超过 10ms，把整批 {@code List<LogEntry>} 作为一条消息发出。
 * 相比逐条发布，一次 AMQP 网络往返 + 一次落盘承载 N 条日志，消除 fsync 与 RTT 瓶颈；
 * 同时保留 publisher confirm——整批一条消息一个 correlation，broker 确认落盘才算安全。
 * <p>
 * 线程安全：攒批操作在锁内（微秒级），发送在锁外并行执行。
 */
@Component
public class LogBatchPublisher {

    private static final Logger log = LoggerFactory.getLogger(LogBatchPublisher.class);

    /** 单批条数上限 */
    static final int BATCH_SIZE = 100;
    /** 攒批最大等待时间（毫秒），保证低流量下日志不滞留 */
    static final long BATCH_TIMEOUT_MS = 10;

    private final RabbitTemplate rabbitTemplate;
    private final List<LogEntry> buffer = new ArrayList<>();
    private long lastFlush = System.currentTimeMillis();

    public LogBatchPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /** 由异步线程池并行调用：入缓冲，满批或超时则换出并发送 */
    public void add(LogEntry entry) {
        List<LogEntry> toSend = null;
        synchronized (this) {
            buffer.add(entry);
            if (buffer.size() >= BATCH_SIZE || System.currentTimeMillis() - lastFlush >= BATCH_TIMEOUT_MS) {
                toSend = new ArrayList<>(buffer);
                buffer.clear();
                lastFlush = System.currentTimeMillis();
            }
        }
        if (toSend != null) {
            publishBatch(toSend);
        }
    }

    /** 兜底定时器：不足满批的尾巴日志，超时后统一刷出 */
    @Scheduled(fixedDelay = 20)
    public void scheduledFlush() {
        List<LogEntry> toSend = null;
        synchronized (this) {
            if (!buffer.isEmpty() && System.currentTimeMillis() - lastFlush >= BATCH_TIMEOUT_MS) {
                toSend = new ArrayList<>(buffer);
                buffer.clear();
                lastFlush = System.currentTimeMillis();
            }
        }
        if (toSend != null) {
            publishBatch(toSend);
        }
    }

    private void publishBatch(List<LogEntry> batch) {
        CorrelationData correlationData = new CorrelationData(UUID.randomUUID().toString());
        correlationData.getFuture().whenComplete((ack, ex) -> {
            if (ex != null || ack == null || !ack.isAck()) {
                log.error("[MQ-CONFIRM-FAIL] 批次({}条)未被 broker 确认落盘，可能丢失 | correlationId={} | cause={}",
                        batch.size(), correlationData.getId(), ex == null ? "broker-nack" : ex.getMessage());
            }
        });
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, batch, correlationData);
    }
}
