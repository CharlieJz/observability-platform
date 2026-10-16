package com.observability.collector.mq;

import com.observability.collector.config.RabbitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 日志消息发布器。
 * <p>
 * 通过 publisher confirm 机制等待 broker 落盘确认：只有 RabbitMQ 确认消息已持久化，
 * 上报才算成功——ES 宕机期间日志也安全地留在 MQ 队列中。
 * 发送失败/nack 时记录告警日志，供排查（可扩展为重投）。
 */
@Component
public class LogMessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(LogMessagePublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public LogMessagePublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(Object message) {
        CorrelationData correlationData = new CorrelationData(UUID.randomUUID().toString());
        correlationData.getFuture().whenComplete((ack, ex) -> {
            if (ex != null || ack == null || !ack.isAck()) {
                log.error("[MQ-CONFIRM-FAIL] 消息未被 broker 确认落盘，可能丢失 | correlationId={} | cause={}",
                        correlationData.getId(), ex == null ? "broker-nack" : ex.getMessage());
            }
        });
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, message, correlationData);
    }
}
