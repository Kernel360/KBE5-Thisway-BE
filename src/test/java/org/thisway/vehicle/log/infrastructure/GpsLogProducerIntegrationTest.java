package org.thisway.vehicle.log.infrastructure;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.*;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.thisway.support.config.RabbitMQConfig;
import org.thisway.support.common.CustomException;
import org.thisway.support.common.ErrorCode;
import org.thisway.vehicle.log.interfaces.GpsLogRequest;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers
class GpsLogProducerIntegrationTest {
    @Container static final GenericContainer<?> RABBIT = new GenericContainer<>("rabbitmq:3.13.7-alpine")
            .withEnv("RABBITMQ_DEFAULT_USER", "test").withEnv("RABBITMQ_DEFAULT_PASS", "test")
            .withExposedPorts(5672).waitingFor(Wait.forLogMessage(".*Server startup complete.*", 1));
    CachingConnectionFactory connection;
    RabbitTemplate template;
    RabbitAdmin admin;
    SimpleMeterRegistry meters;
    GpsLogProducer producer;
    String storage;
    String live;

    @BeforeEach void setup() {
        connection = new CachingConnectionFactory(RABBIT.getHost(), RABBIT.getMappedPort(5672));
        connection.setUsername("test"); connection.setPassword("test");
        var converter = new Jackson2JsonMessageConverter();
        template = new RabbitMQConfig(null).rabbitTemplate(connection, converter);
        meters = new SimpleMeterRegistry();
        producer = new GpsLogProducer(template, converter, mock(Tracer.class), meters);
        admin = new RabbitAdmin(connection);
        admin.declareExchange(new DirectExchange(RabbitMQConfig.GPS_LOG_EXCHANGE));
        admin.declareExchange(new FanoutExchange(RabbitMQConfig.BROADCAST_GPS_LOG_EXCHANGE));
        storage = "publisher-storage-" + UUID.randomUUID();
        live = "publisher-live-" + UUID.randomUUID();
        admin.declareQueue(new Queue(storage));
        admin.declareQueue(new Queue(live));
    }

    @AfterEach void cleanup() {
        admin.deleteQueue(storage); admin.deleteQueue(live);
        admin.deleteExchange(RabbitMQConfig.GPS_LOG_EXCHANGE);
        admin.deleteExchange(RabbitMQConfig.BROADCAST_GPS_LOG_EXCHANGE);
        connection.destroy(); meters.close();
    }

    @Test void 두_경로의_접수를_확인하고_저장메시지는_persistent다() {
        bindStorage();
        admin.declareBinding(new Binding(live, Binding.DestinationType.QUEUE,
                RabbitMQConfig.BROADCAST_GPS_LOG_EXCHANGE, "", null));
        producer.sendGpsLog(request());
        var saved = template.receive(storage, 1000);
        assertThat(saved).isNotNull();
        assertThat(saved.getMessageProperties().getReceivedDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(template.receive(live, 1000)).isNotNull();
        assertThat(meters.counter("gps.publisher.storage.confirmed").count()).isEqualTo(1);
    }

    @Test void 저장_라우팅이_없으면_ack이어도_return을_실패로_처리하고_live는_발행하지_않는다() {
        admin.declareBinding(new Binding(live, Binding.DestinationType.QUEUE,
                RabbitMQConfig.BROADCAST_GPS_LOG_EXCHANGE, "", null));
        assertThatThrownBy(() -> producer.sendGpsLog(request())).isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.GPS_PUBLISH_UNAVAILABLE);
        assertThat(template.receive(live, 100)).isNull();
        assertThat(meters.counter("gps.publisher.storage.unconfirmed").count()).isEqualTo(1);
    }

    @Test void 저장_성공후_live_라우팅이_없어도_저장접수는_성공이고_부분실패를_계수한다() {
        bindStorage();
        assertThatCode(() -> producer.sendGpsLog(request())).doesNotThrowAnyException();
        assertThat(template.receive(storage, 1000)).isNotNull();
        assertThat(meters.counter("gps.publisher.broadcast.unconfirmed").count()).isEqualTo(1);
    }

    @Test void 저장_성공후_live_exchange가_없으면_채널실패를_부분실패로_남긴다() {
        bindStorage();
        admin.deleteExchange(RabbitMQConfig.BROADCAST_GPS_LOG_EXCHANGE);
        assertThatCode(() -> producer.sendGpsLog(request())).doesNotThrowAnyException();
        assertThat(template.receive(storage, 1000)).isNotNull();
        assertThat(meters.counter("gps.publisher.broadcast.unconfirmed").count()).isEqualTo(1);
    }

    private void bindStorage() {
        admin.declareBinding(new Binding(storage, Binding.DestinationType.QUEUE,
                RabbitMQConfig.GPS_LOG_EXCHANGE, RabbitMQConfig.GPS_LOG_ROUTING_KEY, null));
    }
    static GpsLogRequest request() {
        // Serialization-only fixture. Controller/consumer validation is tested separately.
        return new GpsLogRequest("fixture", "fixture", "1", "1", "1", "20200101100000", "0", List.of());
    }
}
