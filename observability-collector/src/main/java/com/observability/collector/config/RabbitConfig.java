package com.observability.collector.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 交换机 / 队列 / 死信队列配置。
 * <p>
 * 核心保障（面试要点）：
 * <ul>
 *   <li>队列 durable：MQ 重启后队列仍存在；</li>
 *   <li>消息持久化：生产者发送时消息落盘（由发送端配合 publisher confirm）；</li>
 *   <li>死信队列：消费端写 ES 失败且不 requeue 的消息进入 DLQ 兜底，避免日志丢失。</li>
 * </ul>
 * 生产者与消费者都声明同一套交换机/队列，声明幂等，属性一致时不会冲突。
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "log.exchange";
    public static final String ROUTING_KEY = "log.routing";
    public static final String QUEUE = "log.queue";
    public static final String DLX = "log.dlx.exchange";
    public static final String DLQ = "log.dlq";
    public static final String DLQ_ROUTING_KEY = "log.dlq.routing";

    /** 日志交换机：direct 类型，durable */
    @Bean
    public DirectExchange logExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    /** 死信交换机：direct 类型，durable */
    @Bean
    public DirectExchange logDlxExchange() {
        return new DirectExchange(DLX, true, false);
    }

    /** 日志队列：durable，绑定死信交换机（消费失败不 requeue 时进 DLQ） */
    @Bean
    public Queue logQueue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(DLQ_ROUTING_KEY)
                .build();
    }

    /** 死信队列：durable，人工/定时重新投递的兜底存储 */
    @Bean
    public Queue logDlq() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Binding logBinding() {
        return BindingBuilder.bind(logQueue()).to(logExchange()).with(ROUTING_KEY);
    }

    @Bean
    public Binding logDlqBinding() {
        return BindingBuilder.bind(logDlq()).to(logDlxExchange()).with(DLQ_ROUTING_KEY);
    }

    /** JSON 消息转换器：日志对象以 JSON 格式进出 MQ */
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
