package org.thisway.log.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.thisway.config.ampq.RabbitMQConfig;
import org.thisway.log.dto.request.gpsLog.GpsLogRequest;
import org.thisway.log.service.LogService;

@Component
@RequiredArgsConstructor
@Slf4j
public class GpsLogConsumer {

    private final LogService logService;

    @RabbitListener(queues = RabbitMQConfig.GPS_LOG_QUEUE)
    public void receiveGpsLog(GpsLogRequest request) {
        log.debug("Received GPS log: {}", request);
        logService.saveGpsLog(request);
    }
}
