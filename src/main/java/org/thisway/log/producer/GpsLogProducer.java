package org.thisway.log.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.thisway.config.ampq.RabbitMQConfig;
import org.thisway.log.dto.request.gpsLog.GpsLogRequest;

@Component
@RequiredArgsConstructor
@Slf4j
public class GpsLogProducer {

    private final RabbitTemplate rabbitTemplate;

    public void sendGpsLog(GpsLogRequest request) {
        log.debug("Sending GPS log to RabbitMQ: {}", request);

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.GPS_LOG_EXCHANGE,
                RabbitMQConfig.GPS_LOG_ROUTING_KEY,
                request
        );
    }
}
