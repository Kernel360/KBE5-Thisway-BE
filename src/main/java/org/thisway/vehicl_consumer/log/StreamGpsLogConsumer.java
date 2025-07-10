package org.thisway.vehicl_consumer.log;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.stereotype.Component;
import org.thisway.vehicle.log.interfaces.GpsLogRequest;
import org.thisway.logging.constant.MdcKeys;
import org.thisway.vehicle.triplog.application.StreamCoordinatesService;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class StreamGpsLogConsumer {

    private final StreamCoordinatesService streamCoordinatesService;

    @RabbitListener(queues = "#{broadcastQueue.name}")
    public void StreamGpsLog(GpsLogRequest request, @Headers Map<String, Object> headers) {
        String traceId = (String) headers.get(MdcKeys.TRACE_ID);
        MDC.put(MdcKeys.TRACE_ID, traceId);

        log.info("Receive Broadcast GPS log: {}", request);
        streamCoordinatesService.sendCurrentCoordinates(request.mdn(), request.cList());
    }
}
