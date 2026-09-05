package org.thisway.support.config;

import org.springframework.amqp.core.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thisway.support.common.RabbitMqGlobalErrorHandler;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.retry.policy.SimpleRetryPolicy;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class RabbitMQConfig {

    public static final String GPS_LOG_QUEUE = "gps_log.queue";
    public static final String GPS_LOG_EXCHANGE = "gps_log.exchange";
    public static final String GPS_LOG_ROUTING_KEY = "gps_log.routingKey";
    public static final String GPS_LOG_DLX = "gps_log.dead.exchange";
    public static final String GPS_LOG_DLQ = "gps_log.dead.queue";
    public static final String GPS_LOG_DEAD_KEY = "gps_log.dead";

    public static final String BROADCAST_GPS_LOG_EXCHANGE = "gps_log.broadcast.exchange";

    private final RabbitMqGlobalErrorHandler rabbitMqGlobalErrorHandler;

    /* Direct Exchange */
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

    // Source queue DLX is attached by broker policy; do not change its immutable arguments.
    // See docs/runbooks/gps-dlq-replay.md before deploying this consumer policy.
    @Bean
    public DirectExchange gpsDeadExchange() {
        return new DirectExchange(GPS_LOG_DLX);
    }

    @Bean
    public Queue gpsDeadQueue() {
        return new Queue(GPS_LOG_DLQ);
    }

    @Bean
    public Binding gpsDeadBinding() {
        return BindingBuilder.bind(gpsDeadQueue()).to(gpsDeadExchange()).with(GPS_LOG_DEAD_KEY);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory gpsSaveListenerContainerFactory(
            ConnectionFactory connectionFactory, Jackson2JsonMessageConverter converter, MeterRegistry meters) {
        var factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        factory.setDefaultRequeueRejected(false);
        factory.setPrefetchCount(1);
        factory.setErrorHandler(error -> {
            // Exception/message bodies can contain raw GPS data. Never log the throwable here.
            log.warn("GPS consumer rejected a delivery; inspect restricted DLQ and rejection metric");
            throw new AmqpRejectAndDontRequeueException("GPS consumer rejected delivery");
        });
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .retryPolicy(new SimpleRetryPolicy(3, Map.of(
                        TransientDataAccessException.class, true,
                        RecoverableDataAccessException.class, true,
                        CannotCreateTransactionException.class, true), true, false))
                .backOffOptions(200, 2.0, 1000)
                .recoverer((message, cause) -> {
                    meters.counter("gps.consumer.rejected").increment();
                    // No cause: the default exception rendering may expose message content.
                    throw new AmqpRejectAndDontRequeueException("GPS consumer retry policy exhausted");
                }).build());
        return factory;
    }

    /* Fanout Exchange */
    @Bean
    public FanoutExchange broadcastExchange() {
        return new FanoutExchange(BROADCAST_GPS_LOG_EXCHANGE);
    }

    @Bean
    public Queue broadcastQueue() {
        return new AnonymousQueue();
    }

    @Bean
    public Binding broadcastBinding() {
        return BindingBuilder
                .bind(broadcastQueue())
                .to(broadcastExchange());
    }

    /* 공통 */
    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter messageConverter
    ) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        return rabbitTemplate;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter jackson2JsonMessageConverter
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jackson2JsonMessageConverter);
        factory.setErrorHandler(rabbitMqGlobalErrorHandler);

        factory.setAdviceChain(
                RetryInterceptorBuilder.stateless()
                        .maxAttempts(3)
                        .recoverer(new RejectAndDontRequeueRecoverer())
                        .build()
        );

        return factory;
    }
}
