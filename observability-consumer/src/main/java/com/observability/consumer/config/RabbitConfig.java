package com.observability.consumer.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 消费端 RabbitMQ 配置。
 * <p>
 * 交换机/队列声明与生产者一致（幂等）；核心是批量监听容器：
 * 攒够 batchSize 条或等待 receiveTimeout 后，将一批消息一起投递给监听器，
 * 由监听器批量写 ES 并统一确认，减少 ES 写入次数、降低 RTT。
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "log.exchange";
    public static final String ROUTING_KEY = "log.routing";
    public static final String QUEUE = "log.queue";
    public static final String DLX = "log.dlx.exchange";
    public static final String DLQ = "log.dlq";
    public static final String DLQ_ROUTING_KEY = "log.dlq.routing";

    @Bean
    public DirectExchange logExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange logDlxExchange() {
        return new DirectExchange(DLX, true, false);
    }

    /** 日志队列：durable + 死信绑定（写 ES 失败不 requeue 时进 DLQ） */
    @Bean
    public Queue logQueue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(DLQ_ROUTING_KEY)
                .build();
    }

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

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * 批量监听容器工厂：攒批（200 条或 1 秒）后一次性投递，
     * 手动确认模式交由监听器在写 ES 成功后统一 ACK。
     */
    @Bean("batchContainerFactory")
    public SimpleRabbitListenerContainerFactory batchContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAcknowledgeMode(org.springframework.amqp.core.AcknowledgeMode.MANUAL);
        factory.setBatchListener(true);
        factory.setConsumerBatchEnabled(true);
        factory.setBatchSize(200);
        factory.setReceiveTimeout(1000L);
        return factory;
    }
}
