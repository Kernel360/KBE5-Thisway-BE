package org.thisway.vehicle.log.infrastructure;

import org.thisway.emulator.credential.DeviceIdentity;
import io.micrometer.tracing.Tracer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.core.MessageDeliveryMode;
import io.micrometer.core.instrument.MeterRegistry;
import org.thisway.support.common.CustomException;
import org.thisway.support.common.ErrorCode;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.stereotype.Component;
import org.thisway.support.config.RabbitMQConfig;
import org.thisway.vehicle.log.interfaces.GpsLogRequest;
import org.thisway.support.logging.constant.MdcKeys;

@Component
@RequiredArgsConstructor
@Slf4j
public class GpsLogProducer {

    private final RabbitTemplate rabbitTemplate;
    private final MessageConverter messageConverter;
    private final Tracer tracer;
    private final MeterRegistry meters;
    private static final long BUDGET_NANOS = TimeUnit.SECONDS.toNanos(2);
    // Bound blocked broker calls as well as queued packets; caller threads never run broker I/O.
    private final java.util.concurrent.ThreadPoolExecutor publishes = new java.util.concurrent.ThreadPoolExecutor(
            4, 4, 0, TimeUnit.SECONDS, new java.util.concurrent.ArrayBlockingQueue<>(64),
            task -> { var thread = new Thread(task, "gps-publisher"); thread.setDaemon(true); return thread; },
            new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());

    @jakarta.annotation.PostConstruct
    void registerCapacityMetrics() {
        meters.gauge("gps.publisher.active", publishes, java.util.concurrent.ThreadPoolExecutor::getActiveCount);
        meters.gauge("gps.publisher.queued", publishes, executor -> executor.getQueue().size());
    }

    @jakarta.annotation.PreDestroy
    public void close() { publishes.shutdownNow(); }


    public void sendGpsLog(GpsLogRequest request, DeviceIdentity identity) {
        var properties = new MessageProperties();
        properties.getHeaders().putAll(GpsMessageIdentity.headers(identity));
        if (!identity.mdn().equals(request.mdn())) throw new CustomException(ErrorCode.DEVICE_AUTHENTICATION_FAILED);
        long deadline = System.nanoTime() + BUDGET_NANOS;
        String traceId = org.slf4j.MDC.get(MdcKeys.TRACE_ID);
        if (traceId == null && tracer.currentSpan() != null) traceId = tracer.currentSpan().context().traceId();
        try (var context = org.thisway.support.logging.TraceContext.open(traceId)) {
            traceId = org.slf4j.MDC.get(MdcKeys.TRACE_ID);
        }
        properties.setHeader(MdcKeys.TRACE_ID, traceId);
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        Message message = messageConverter.toMessage(request, properties);
        var storageConfirmed = new java.util.concurrent.atomic.AtomicBoolean();
        java.util.concurrent.Future<?> task;
        try {
            task = publishes.submit(() -> {
                try (var context = org.thisway.support.logging.TraceContext.open(message.getMessageProperties().getHeader(MdcKeys.TRACE_ID))) {
                    send(message, deadline, storageConfirmed);
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException saturated) {
            meters.counter("gps.publisher.saturated").increment();
            throw new CustomException(ErrorCode.GPS_PUBLISH_UNAVAILABLE);
        }
        try {
            task.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            task.cancel(true); publishes.remove((Runnable) task);
        } catch (java.util.concurrent.TimeoutException timeout) {
            meters.counter("gps.publisher.deadline").increment();
            task.cancel(true); publishes.remove((Runnable) task);
        } catch (java.util.concurrent.ExecutionException failed) {
            // Keep broker exception internals private.
        }
        if (!storageConfirmed.get()) throw new CustomException(ErrorCode.GPS_PUBLISH_UNAVAILABLE);
    }

    private void send(Message message, long deadline, java.util.concurrent.atomic.AtomicBoolean storageConfirmed) {
        if (!confirmed(RabbitMQConfig.GPS_LOG_EXCHANGE, RabbitMQConfig.GPS_LOG_ROUTING_KEY, message, deadline)) {
            meters.counter("gps.publisher.storage.unconfirmed").increment();
            return;
        }
        storageConfirmed.set(true);
        meters.counter("gps.publisher.storage.confirmed").increment();
        // Both sends share the caller's one deadline; live delivery remains best effort.
        if (!confirmed(RabbitMQConfig.BROADCAST_GPS_LOG_EXCHANGE, "", message, deadline)) {
            meters.counter("gps.publisher.broadcast.unconfirmed").increment();
            log.warn("GPS 실시간 전달 미확인: 저장용 broker 접수는 확인됨");
        }
    }

    private boolean confirmed(String exchange, String routingKey, Message message, long deadline) {
        var correlation = new CorrelationData(UUID.randomUUID().toString());
        try {
            if (System.nanoTime() >= deadline || Thread.currentThread().isInterrupted()) return false;
            rabbitTemplate.send(exchange, routingKey, message, correlation);
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) return false;
            var confirm = correlation.getFuture().get(remaining, TimeUnit.NANOSECONDS);
            return confirm.isAck() && correlation.getReturned() == null;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception failure) {
            return false;
        }
    }
}
