package org.thisway.log.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.thisway.log.dto.request.gpsLog.GpsLogRequest;
import org.thisway.log.producer.GpsLogProducer;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "gps-log-collect-mode", havingValue = "rabbitmq")
public class RabbitMqGpsLogService implements GpsLogService {

    private final GpsLogProducer gpsLogProducer;

    @Override
    public void saveGpsLog(GpsLogRequest request) {
        gpsLogProducer.sendGpsLog(request);
    }
}
