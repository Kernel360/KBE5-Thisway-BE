package org.thisway.vehicle.log.infrastructure;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.thisway.support.common.CustomException;
import org.thisway.support.config.RabbitMQConfig;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GpsLogProducerTest {
    @Test void nack과_confirm_미도착은_성공으로_응답하지_않는다() {
        for (boolean nack : new boolean[]{true, false}) {
            var template = mock(RabbitTemplate.class);
            doAnswer(call -> {
                CorrelationData correlation = call.getArgument(3);
                if (nack) correlation.getFuture().complete(new CorrelationData.Confirm(false, "fixture"));
                return null; // Otherwise the real two-second future timeout expires.
            }).when(template).send(eq(RabbitMQConfig.GPS_LOG_EXCHANGE), anyString(), any(Message.class), any(CorrelationData.class));
            var producer = new GpsLogProducer(template, new Jackson2JsonMessageConverter(), mock(Tracer.class), new SimpleMeterRegistry());
            assertThatThrownBy(() -> producer.sendGpsLog(GpsLogProducerIntegrationTest.request())).isInstanceOf(CustomException.class);
            verify(template, never()).send(eq(RabbitMQConfig.BROADCAST_GPS_LOG_EXCHANGE), anyString(), any(Message.class), any(CorrelationData.class));
        }
    }
}
