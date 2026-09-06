package org.thisway.vehicle.log.infrastructure;

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

    public void sendGpsLog(GpsLogRequest request) {
        log.debug("GPS 로그 RabbitMQ 전송: 항목 수={}", request.cCnt());

        String traceId = tracer.currentSpan() != null
                ? tracer.currentSpan().context().traceId()
                : "unknown";

        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setHeader(MdcKeys.TRACE_ID, traceId);
        messageProperties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);

        Message message = messageConverter.toMessage(request, messageProperties);

        if (!confirmed(RabbitMQConfig.GPS_LOG_EXCHANGE, RabbitMQConfig.GPS_LOG_ROUTING_KEY, message)) {
            meters.counter("gps.publisher.storage.unconfirmed").increment();
            throw new CustomException(ErrorCode.GPS_PUBLISH_UNAVAILABLE);
        }
        meters.counter("gps.publisher.storage.confirmed").increment();
        // Live delivery is best effort, not a second durable storage success condition.
        if (!confirmed(RabbitMQConfig.BROADCAST_GPS_LOG_EXCHANGE, "", message)) {
            meters.counter("gps.publisher.broadcast.unconfirmed").increment();
            log.warn("GPS 실시간 전달 미확인: 저장용 broker 접수는 확인됨");
        }
    }

    private boolean confirmed(String exchange, String routingKey, Message message) {
        var correlation = new CorrelationData(UUID.randomUUID().toString());
        try {
            rabbitTemplate.send(exchange, routingKey, message, correlation);
            var confirm = correlation.getFuture().get(2, TimeUnit.SECONDS);
            return confirm.isAck() && correlation.getReturned() == null;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception failure) {
            // AMQP exception bodies may include packet data. Do not attach them to logs/responses.
            return false;
        }
    }
}
