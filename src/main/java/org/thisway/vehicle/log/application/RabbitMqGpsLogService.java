package org.thisway.vehicle.log.application;

import org.thisway.emulator.credential.DeviceIdentity;
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
    public void saveGpsLog(GpsLogRequest request, DeviceIdentity identity) {
        gpsLogProducer.sendGpsLog(request, identity);
    }
}
