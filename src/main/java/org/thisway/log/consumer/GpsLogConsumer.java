package org.thisway.log.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.thisway.config.ampq.RabbitMQConfig;
import org.thisway.log.dto.request.gpsLog.GpsLogRequest;
import org.thisway.log.service.GpsLogSaveService;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "gps-log-collect-mode", havingValue = "rabbitmq")
@Slf4j
public class GpsLogConsumer {

    private final GpsLogSaveService gpsLogSaveService;

    @RabbitListener(queues = RabbitMQConfig.GPS_LOG_QUEUE)
    public void receiveGpsLog(GpsLogRequest request) {
        log.debug("Received GPS log: {}", request);
        gpsLogSaveService.saveGpsLog(request);
    }
}
