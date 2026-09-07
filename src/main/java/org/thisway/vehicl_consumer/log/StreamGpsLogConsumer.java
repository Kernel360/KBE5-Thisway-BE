package org.thisway.vehicl_consumer.log;

import org.thisway.vehicle.log.application.DeviceTelemetryService;
import org.thisway.vehicle.log.infrastructure.GpsMessageIdentity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.stereotype.Component;
import org.thisway.vehicle.log.interfaces.GpsLogRequest;
import org.thisway.support.logging.constant.MdcKeys;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class StreamGpsLogConsumer {

    private final DeviceTelemetryService telemetry;

    @RabbitListener(queues = "#{broadcastQueue.name}", containerFactory = "gpsStreamListenerContainerFactory")
    public void StreamGpsLog(GpsLogRequest request, @Headers Map<String, Object> headers) {
        String traceId = headers.get(MdcKeys.TRACE_ID) instanceof String value ? value : null;
        MDC.put(MdcKeys.TRACE_ID, traceId);

        try {
            log.debug("GPS 방송 메시지 수신");
            telemetry.streamGps(request, GpsMessageIdentity.read(headers, request.mdn()));
        } finally {
            MDC.remove(MdcKeys.TRACE_ID);
        }
    }
}
