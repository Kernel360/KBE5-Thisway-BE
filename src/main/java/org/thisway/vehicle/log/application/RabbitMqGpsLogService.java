package org.thisway.vehicle.log.application;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.thisway.vehicle.log.interfaces.GpsLogRequest;
import org.thisway.vehicle.log.infrastructure.GpsLogProducer;

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
