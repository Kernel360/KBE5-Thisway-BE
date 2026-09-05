package org.thisway.support.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;
import org.thisway.support.common.RabbitMqGlobalErrorHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RabbitMQConfigTest {

    @Test
    void 저장_listener는_전용_factory를_쓰고_source_queue_arguments는_유지한다() throws Exception {
        var config = new RabbitMQConfig(null);
        assertThat(config.gpsLogQueue().getArguments()).isNullOrEmpty();
        assertThat(config.gpsDeadQueue().isDurable()).isTrue();
        assertThat(config.gpsDeadQueue().getArguments()).isNullOrEmpty();
        var listener = org.thisway.vehicl_consumer.log.SaveGpsLogConsumer.class
                .getMethod("receiveGpsLog", org.thisway.vehicle.log.interfaces.GpsLogRequest.class, java.util.Map.class)
                .getAnnotation(org.springframework.amqp.rabbit.annotation.RabbitListener.class);
        assertThat(listener.containerFactory()).isEqualTo("gpsSaveListenerContainerFactory");
        assertThat(config.gpsDeadBinding().getDestination()).isEqualTo(RabbitMQConfig.GPS_LOG_DLQ);
        assertThat(config.gpsDeadBinding().getRoutingKey()).isEqualTo(RabbitMQConfig.GPS_LOG_DEAD_KEY);
    }

    @Test
    void 각_application_instance는_서로_다른_broadcast_queue를_사용한다() {
        RabbitMQConfig firstInstance = new RabbitMQConfig(mock(RabbitMqGlobalErrorHandler.class));
        RabbitMQConfig secondInstance = new RabbitMQConfig(mock(RabbitMqGlobalErrorHandler.class));

        Queue firstQueue = firstInstance.broadcastQueue();
        Queue secondQueue = secondInstance.broadcastQueue();

        assertThat(firstQueue.getName()).isNotEqualTo(secondQueue.getName());
        assertThat(firstQueue.isExclusive()).isTrue();
        assertThat(firstQueue.isAutoDelete()).isTrue();
        assertThat(secondQueue.isExclusive()).isTrue();
        assertThat(secondQueue.isAutoDelete()).isTrue();
    }
}
