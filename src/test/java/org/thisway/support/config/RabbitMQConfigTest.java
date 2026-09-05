package org.thisway.support.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;
import org.thisway.support.common.RabbitMqGlobalErrorHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RabbitMQConfigTest {

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
