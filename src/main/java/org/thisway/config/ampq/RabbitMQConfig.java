package org.thisway.config.ampq;

import java.util.List;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class RabbitMQConfig {

    public static final String GPS_LOG_QUEUE = "gps_log.queue";
    public static final String GPS_LOG_EXCHANGE = "gps_log.exchange";
    public static final String GPS_LOG_ROUTING_KEY = "gps_log.routingKey";

    // @Bean
    // public TaskExecutor publisherTaskExecutor() {
    // ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    // executor.setCorePoolSize(10); // 원하는 퍼블리셔 수
    // executor.setMaxPoolSize(50);
    // executor.initialize();
    // return executor;
    // }
    //
    // public void publishConcurrently(
    // List<Message> messages,
    // RabbitTemplate amqpTemplate) {
    // for (Message msg : messages) {
    // publisherTaskExecutor().execute(() -> {
    // amqpTemplate.convertAndSend(GPS_LOG_ROUTING_KEY, msg);
    // });
    // }
    // }

    @Bean
    public Queue gpsLogQueue() {
        return new Queue(GPS_LOG_QUEUE);
    }

    @Bean
    public DirectExchange gpsLogExchange() {
        return new DirectExchange(GPS_LOG_EXCHANGE);
    }

    @Bean
    public Binding gpsLogBinding() {
        return BindingBuilder
                .bind(gpsLogQueue())
                .to(gpsLogExchange())
                .with(GPS_LOG_ROUTING_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        return rabbitTemplate;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter jackson2JsonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jackson2JsonMessageConverter);
        return factory;
    }
}
