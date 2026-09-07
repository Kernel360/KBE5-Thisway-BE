package org.thisway.vehicl_consumer.log;

import org.thisway.vehicle.log.infrastructure.GpsMessageIdentity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.stereotype.Component;
import org.thisway.support.config.RabbitMQConfig;
import org.thisway.vehicle.log.interfaces.GpsLogRequest;
import org.thisway.vehicle.log.application.GpsLogSaveService;
import org.thisway.support.logging.constant.MdcKeys;

import java.util.Map;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "gps-log-collect-mode", havingValue = "rabbitmq")
@Slf4j
public class SaveGpsLogConsumer {

    private final GpsLogSaveService gpsLogSaveService;
    private final io.micrometer.core.instrument.MeterRegistry meters;

    @RabbitListener(queues = RabbitMQConfig.GPS_LOG_QUEUE, concurrency = "2-5",
            containerFactory = "gpsSaveListenerContainerFactory")
    public void receiveGpsLog(GpsLogRequest request, @Headers Map<String, Object> headers) {
        String traceId = headers.get(MdcKeys.TRACE_ID) instanceof String value ? value : null;
        long started = System.nanoTime();
        String outcome = "failed";
        try (var context = org.thisway.support.logging.TraceContext.open(traceId)) {
            log.debug("GPS 저장 메시지 수신");
            gpsLogSaveService.saveGpsLog(request,
                    GpsMessageIdentity.read(headers, request.mdn()));
            outcome = "committed"; // Transactional service proxy returned after commit.
        } finally {
            io.micrometer.core.instrument.Timer.builder("gps.consumer.processing")
                    .tag("outcome", outcome).publishPercentileHistogram()
                    .register(meters).record(System.nanoTime() - started, java.util.concurrent.TimeUnit.NANOSECONDS);
        }
    }
}
