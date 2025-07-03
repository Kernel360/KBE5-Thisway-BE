package org.thisway.log.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.stereotype.Component;
import org.thisway.config.ampq.RabbitMQConfig;
import org.thisway.log.dto.request.gpsLog.GpsLogRequest;
import org.thisway.log.service.GpsLogSaveService;
import org.thisway.logging.constant.MdcKeys;

import java.util.Map;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "gps-log-collect-mode", havingValue = "rabbitmq")
@Slf4j
public class SaveGpsLogConsumer {

    private final GpsLogSaveService gpsLogSaveService;

    @RabbitListener(queues = RabbitMQConfig.GPS_LOG_QUEUE, concurrency = "5")
    public void receiveGpsLog(GpsLogRequest request, @Headers Map<String, Object> headers) {
        String traceId = (String) headers.get(MdcKeys.TRACE_ID);
        MDC.put(MdcKeys.TRACE_ID, traceId);

        log.debug("Received GPS log: {}", request);
        gpsLogSaveService.saveGpsLog(request);
    }
}
