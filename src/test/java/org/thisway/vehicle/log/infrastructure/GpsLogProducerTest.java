package org.thisway.vehicle.log.infrastructure;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.thisway.support.common.CustomException;
import org.thisway.support.config.RabbitMQConfig;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GpsLogProducerTest {
    @Test void nack과_confirm_미도착은_성공으로_응답하지_않는다() {
        for (boolean nack : new boolean[]{true, false}) {
            var template = mock(RabbitTemplate.class);
            doAnswer(call -> {
                CorrelationData correlation = call.getArgument(3);
                if (nack) correlation.getFuture().complete(new CorrelationData.Confirm(false, "fixture"));
                return null; // Otherwise the real two-second future timeout expires.
            }).when(template).send(eq(RabbitMQConfig.GPS_LOG_EXCHANGE), anyString(), any(Message.class), any(CorrelationData.class));
            var producer = new GpsLogProducer(template, new Jackson2JsonMessageConverter(), mock(Tracer.class), new SimpleMeterRegistry());
            assertThatThrownBy(() -> producer.sendGpsLog(GpsLogProducerIntegrationTest.request(), GpsLogProducerIntegrationTest.identity())).isInstanceOf(CustomException.class);
            verify(template, never()).send(eq(RabbitMQConfig.BROADCAST_GPS_LOG_EXCHANGE), anyString(), any(Message.class), any(CorrelationData.class));
            producer.close();
        }
    }
    @Test void blockedBrokerSendCannotHoldCallerPastSharedBudget() throws Exception {
        var template = mock(RabbitTemplate.class);
        var release = new java.util.concurrent.CountDownLatch(1);
        doAnswer(call -> {
            // Model a socket/library call that ignores interruption until the connection recovers.
            while (release.getCount() > 0) {
                try { release.await(); } catch (InterruptedException ignored) { }
            }
            return null;
        }).when(template).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        var producer = new GpsLogProducer(template, new Jackson2JsonMessageConverter(), mock(Tracer.class), new SimpleMeterRegistry());
        long started = System.nanoTime();
        try {
            assertThatThrownBy(() -> producer.sendGpsLog(GpsLogProducerIntegrationTest.request(), GpsLogProducerIntegrationTest.identity()))
                    .isInstanceOf(CustomException.class);
            assertThat(java.time.Duration.ofNanos(System.nanoTime() - started).toMillis()).isBetween(1800L, 3500L);
        } finally { release.countDown(); producer.close(); }
    }

    @Test void livePublishUsesRemainingBudgetAndCannotUndoConfirmedStorage() {
        var template = mock(RabbitTemplate.class);
        doAnswer(call -> {
            Thread.sleep(1500);
            ((CorrelationData) call.getArgument(3)).getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(template).send(eq(RabbitMQConfig.GPS_LOG_EXCHANGE), anyString(), any(Message.class), any(CorrelationData.class));
        // Live send returns immediately, but its confirm never arrives.
        var producer = new GpsLogProducer(template, new Jackson2JsonMessageConverter(), mock(Tracer.class), new SimpleMeterRegistry());
        long started = System.nanoTime();
        try {
            assertThatCode(() -> producer.sendGpsLog(GpsLogProducerIntegrationTest.request(), GpsLogProducerIntegrationTest.identity())).doesNotThrowAnyException();
            assertThat(java.time.Duration.ofNanos(System.nanoTime() - started).toMillis()).isBetween(1800L, 3200L);
            verify(template).send(eq(RabbitMQConfig.BROADCAST_GPS_LOG_EXCHANGE), anyString(), any(Message.class), any(CorrelationData.class));
        } finally { producer.close(); }
    }

    @Test void blockedBrokerCapacityIsFiniteAndOverloadIsRejected() throws Exception {
        var template = mock(RabbitTemplate.class);
        var release = new java.util.concurrent.CountDownLatch(1);
        var entered = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(call -> {
            entered.incrementAndGet();
            while (release.getCount() > 0) {
                try { release.await(); } catch (InterruptedException ignored) { }
            }
            return null;
        }).when(template).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        var meters = new SimpleMeterRegistry();
        var producer = new GpsLogProducer(template, new Jackson2JsonMessageConverter(), mock(Tracer.class), meters);
        try (var callers = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var start = new java.util.concurrent.CountDownLatch(1);
            var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 72; i++) futures.add(callers.submit(() -> {
                start.await();
                assertThatThrownBy(() -> producer.sendGpsLog(GpsLogProducerIntegrationTest.request(), GpsLogProducerIntegrationTest.identity()))
                        .isInstanceOf(CustomException.class);
                return null;
            }));
            start.countDown();
            for (var future : futures) future.get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(entered.get()).isEqualTo(4);
            assertThat(meters.counter("gps.publisher.saturated").count()).isGreaterThanOrEqualTo(4);
        } finally { release.countDown(); producer.close(); meters.close(); }
    }

}
