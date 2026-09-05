package org.thisway.support.component.streaming;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.thisway.support.config.RabbitMQConfig;

import java.nio.charset.StandardCharsets;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
class SseFanoutIntegrationTest {
    @Container
    static final GenericContainer<?> BROKER = new GenericContainer<>("rabbitmq:3.13.7-alpine")
            .withEnv("RABBITMQ_DEFAULT_USER", "test")
            .withEnv("RABBITMQ_DEFAULT_PASS", "test")
            .withExposedPorts(5672)
            .waitingFor(Wait.forLogMessage(".*Server startup complete.*", 1));

    @Test
    void 서로_다른_JVM의_구독자가_같은_방송을_수신한다() throws Exception {
        try (ForkedSubscriber first = new ForkedSubscriber();
             ForkedSubscriber second = new ForkedSubscriber();
             Subscriber publisherConnection = new Subscriber()) {
            RabbitTemplate publisher = new RabbitTemplate(publisherConnection.factory);
            publisher.convertAndSend(RabbitMQConfig.BROADCAST_GPS_LOG_EXCHANGE, "", "forked");
            first.expectEvent();
            second.expectEvent();
        }
    }

    private static class ForkedSubscriber implements AutoCloseable {
        final Process process;
        final LinkedBlockingQueue<String> markers = new LinkedBlockingQueue<>();

        ForkedSubscriber() throws Exception {
            process = new ProcessBuilder(
                    ProcessHandle.current().info().command().orElseThrow(),
                    "-cp", System.getProperty("sse.test.classpath"),
                    Worker.class.getName(), BROKER.getHost(), BROKER.getMappedPort(5672).toString())
                    .redirectErrorStream(true).start();
            Thread.ofVirtual().start(() -> {
                try (BufferedReader reader = process.inputReader()) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("SSE_TEST_")) markers.add(line);
                    }
                } catch (Exception ignored) {
                    markers.add("SSE_TEST_READER_FAILED");
                }
            });
            try {
                assertThat(markers.poll(20, TimeUnit.SECONDS)).isEqualTo("SSE_TEST_READY");
            } catch (Throwable failure) {
                close();
                throw failure;
            }
        }

        void expectEvent() throws Exception {
            PrintWriter writer = new PrintWriter(process.getOutputStream(), true);
            writer.println("READ");
            assertThat(markers.poll(15, TimeUnit.SECONDS))
                    .isEqualTo("SSE_TEST_EVENT:dashboard_gps_stream:forked");
        }

        public void close() throws Exception {
            process.getOutputStream().close();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        }
    }

    public static class Worker {
        public static void main(String[] args) throws Exception {
            try (Subscriber subscriber = new Subscriber(args[0], Integer.parseInt(args[1]));
                 BufferedReader input = new BufferedReader(new InputStreamReader(System.in))) {
                System.out.println("SSE_TEST_READY");
                if (input.readLine() != null) {
                    await().atMost(Duration.ofSeconds(10)).until(() -> !subscriber.received.isEmpty());
                    System.out.println("SSE_TEST_EVENT:" + subscriber.received.getFirst());
                    input.readLine();
                }
            }
        }
    }

    @Test
    void 두_독립_구독에_방송하고_한쪽_종료후에도_나머지는_수신한다() {
        try (Subscriber first = new Subscriber(); Subscriber second = new Subscriber()) {
            RabbitTemplate publisher = new RabbitTemplate(second.factory);
            publisher.convertAndSend(RabbitMQConfig.BROADCAST_GPS_LOG_EXCHANGE, "", "first");
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                assertThat(first.received).containsExactly("dashboard_gps_stream:first");
                assertThat(second.received).containsExactly("dashboard_gps_stream:first");
            });

            first.close();
            RabbitAdmin admin = new RabbitAdmin(second.factory);
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                    assertThat(admin.getQueueProperties(first.queue.getName())).isNull());
            publisher.convertAndSend(RabbitMQConfig.BROADCAST_GPS_LOG_EXCHANGE, "", "second");
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                    assertThat(second.received).containsExactly(
                            "dashboard_gps_stream:first", "dashboard_gps_stream:second"));
            assertThat(first.received).containsExactly("dashboard_gps_stream:first");

            try (Subscriber reconnected = new Subscriber()) {
                assertThat(reconnected.queue.getName()).isNotEqualTo(first.queue.getName());
                publisher.convertAndSend(RabbitMQConfig.BROADCAST_GPS_LOG_EXCHANGE, "", "third");
                await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                    assertThat(reconnected.received).containsExactly("dashboard_gps_stream:third");
                    assertThat(second.received).hasSize(3);
                });
            }
        }
    }

    private static class Subscriber implements AutoCloseable {
        final CachingConnectionFactory factory;
        final List<String> received = new CopyOnWriteArrayList<>();
        final SimpleMessageListenerContainer listener;
        final Queue queue;
        boolean closed;

        Subscriber() {
            this(BROKER.getHost(), BROKER.getMappedPort(5672));
        }

        Subscriber(String host, int port) {
            factory = new CachingConnectionFactory(host, port);
            factory.setUsername("test");
            factory.setPassword("test");
            RabbitMQConfig config = new RabbitMQConfig(null);
            queue = config.broadcastQueue();
            RabbitAdmin admin = new RabbitAdmin(factory);
            admin.declareExchange(config.broadcastExchange());
            admin.declareQueue(queue);
            admin.declareBinding(BindingBuilder.bind(queue).to(config.broadcastExchange()));
            SseConnection connections = new SseConnection(SseEmitter::new, 256,
                    (emitter, name, data) -> received.add(name + ":" + data));
            connections.createSseEmitter("company:1:member:tab");
            connections.markInitialChunkComplete("company:1:member:tab");
            SseEventSender sender = new SseEventSender(connections);
            listener = new SimpleMessageListenerContainer(factory);
            listener.setQueueNames(queue.getName());
            listener.setMessageListener((org.springframework.amqp.core.MessageListener) message ->
                    sender.sendToPrefix("company:1", "dashboard_gps_stream",
                            new String(message.getBody(), StandardCharsets.UTF_8)));
            listener.start();
            await().atMost(Duration.ofSeconds(10)).until(() -> listener.getActiveConsumerCount() == 1);
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                listener.stop();
                factory.destroy();
            }
        }
    }
}
