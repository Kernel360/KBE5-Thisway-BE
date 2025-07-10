package org.thisway.vehicle.log.infrastructure;

import io.micrometer.tracing.Tracer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.stereotype.Component;
import org.thisway.config.ampq.RabbitMQConfig;
import org.thisway.vehicle.log.interfaces.GpsLogRequest;
import org.thisway.logging.constant.MdcKeys;

@Component
@RequiredArgsConstructor
@Slf4j
public class GpsLogProducer {

    private final RabbitTemplate rabbitTemplate;
    private final MessageConverter messageConverter;
    private final Tracer tracer;

    public void sendGpsLog(GpsLogRequest request) {
        log.debug("Sending GPS log to RabbitMQ: {}", request);

        String traceId = tracer.currentSpan() != null
                ? tracer.currentSpan().context().traceId()
                : "unknown";

        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setHeader(MdcKeys.TRACE_ID, traceId);

        Message message = messageConverter.toMessage(request, messageProperties);

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.GPS_LOG_EXCHANGE,
                RabbitMQConfig.GPS_LOG_ROUTING_KEY,
                message
        );

        rabbitTemplate.send(
                RabbitMQConfig.BROADCAST_GPS_LOG_EXCHANGE,
                "",
                message
        );
    }
}
