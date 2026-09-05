package org.thisway.support.component.streaming;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SseConnectionTest {
    @ParameterizedTest
    @ValueSource(strings = {"vehicle", "company"})
    void resourceId의_구분자까지_일치하는_구독만_선택한다(String category) {
        SseConnection connections = new SseConnection();
        connections.createSseEmitter(category + ":1:alice");
        connections.createSseEmitter(category + ":1:bob");
        connections.createSseEmitter(category + ":10:alice");
        connections.createSseEmitter(category + ":100:alice");
        assertThat(connections.findKeysByPrefix(category + ":1"))
                .containsExactlyInAnyOrder(category + ":1:alice", category + ":1:bob");
        assertThat(connections.findKeysByPrefix(category + ":1:"))
                .containsExactlyInAnyOrder(category + ":1:alice", category + ":1:bob");
    }

    @ParameterizedTest
    @ValueSource(strings = {"completion", "timeout", "error"})
    void 이전_연결의_콜백은_재접속한_연결을_삭제하지_않는다(String event) {
        SseEmitter previous = mock(SseEmitter.class);
        SseEmitter current = mock(SseEmitter.class);
        AtomicInteger index = new AtomicInteger();
        SseConnection connections = new SseConnection(() -> index.getAndIncrement() == 0 ? previous : current);
        String key = "vehicle:1:alice";
        connections.createSseEmitter(key);
        connections.createSseEmitter(key);
        verify(previous).complete();
        fire(previous, event);
        assertThat(connections.get(key)).contains(current);
        fire(current, event);
        assertThat(connections.get(key)).isEmpty();
    }

    @Test
    void 서로_다른_구독은_독립적으로_유지된다() {
        SseConnection connections = new SseConnection();
        SseEmitter first = connections.createSseEmitter("vehicle:1:alice");
        connections.createSseEmitter("vehicle:1:bob");
        assertThat(connections.get("vehicle:1:alice")).contains(first);
        assertThat(connections.getAllKeys()).hasSize(2);
    }

    @Test
    void 초기화_중인_이벤트는_이름과_FIFO_순서를_보존해_전송한다() {
        List<WrittenEvent> writtenEvents = new CopyOnWriteArrayList<>();
        SseConnection connections = new SseConnection(
                SseEmitter::new,
                3,
                (emitter, eventName, data) -> writtenEvents.add(new WrittenEvent(eventName, data))
        );
        String key = "vehicle:1:alice";
        connections.createSseEmitter(key);

        assertThat(connections.sendLiveEvent(key, "vehicle_detail_gps_stream", "first"))
                .isEqualTo(SseConnection.DeliveryResult.BUFFERED);
        assertThat(connections.sendLiveEvent(key, "dashboard_gps_stream", "second"))
                .isEqualTo(SseConnection.DeliveryResult.BUFFERED);
        assertThat(writtenEvents).isEmpty();

        connections.markInitialChunkComplete(key);
        assertThat(connections.sendLiveEvent(key, "vehicle_detail_gps_stream", "third"))
                .isEqualTo(SseConnection.DeliveryResult.SENT);

        assertThat(writtenEvents).containsExactly(
                new WrittenEvent("vehicle_detail_gps_stream", "first"),
                new WrittenEvent("dashboard_gps_stream", "second"),
                new WrittenEvent("vehicle_detail_gps_stream", "third")
        );
    }

    @Test
    void 초기화_버퍼가_상한을_넘으면_연결을_종료하고_등록을_해제한다() {
        SseEmitter emitter = mock(SseEmitter.class);
        SseConnection connections = new SseConnection(() -> emitter, 2, (target, eventName, data) -> {
        });
        String key = "vehicle:1:alice";
        connections.createSseEmitter(key);

        assertThat(connections.sendLiveEvent(key, "gps", "first"))
                .isEqualTo(SseConnection.DeliveryResult.BUFFERED);
        assertThat(connections.sendLiveEvent(key, "gps", "second"))
                .isEqualTo(SseConnection.DeliveryResult.BUFFERED);
        assertThat(connections.sendLiveEvent(key, "gps", "overflow"))
                .isEqualTo(SseConnection.DeliveryResult.OVERFLOW);

        assertThat(connections.get(key)).isEmpty();
        verify(emitter).completeWithError(isA(IllegalStateException.class));
    }

    @Test
    void 버퍼_flush와_동시에_도착한_live_event는_유실되거나_앞지르지_않는다() throws Exception {
        CountDownLatch firstWriteStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstWrite = new CountDownLatch(1);
        CountDownLatch concurrentSendStarted = new CountDownLatch(1);
        List<WrittenEvent> writtenEvents = new CopyOnWriteArrayList<>();
        SseConnection connections = new SseConnection(
                SseEmitter::new,
                3,
                (emitter, eventName, data) -> {
                    writtenEvents.add(new WrittenEvent(eventName, data));
                    if (data.equals("buffered")) {
                        firstWriteStarted.countDown();
                        try {
                            if (!releaseFirstWrite.await(2, SECONDS)) {
                                throw new IllegalStateException("test writer release timed out");
                            }
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("test writer interrupted", exception);
                        }
                    }
                }
        );
        String key = "vehicle:1:alice";
        connections.createSseEmitter(key);
        connections.sendLiveEvent(key, "vehicle_detail_gps_stream", "buffered");
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> flush = executor.submit(() -> connections.markInitialChunkComplete(key));
            assertThat(firstWriteStarted.await(2, SECONDS)).isTrue();
            Future<SseConnection.DeliveryResult> concurrentSend = executor.submit(() -> {
                concurrentSendStarted.countDown();
                return connections.sendLiveEvent(key, "vehicle_detail_gps_stream", "live");
            });
            assertThat(concurrentSendStarted.await(2, SECONDS)).isTrue();
            assertThat(concurrentSend).isNotDone();

            releaseFirstWrite.countDown();
            flush.get(2, SECONDS);
            assertThat(concurrentSend.get(2, SECONDS)).isEqualTo(SseConnection.DeliveryResult.SENT);
        } finally {
            releaseFirstWrite.countDown();
            executor.shutdownNow();
        }

        assertThat(writtenEvents).containsExactly(
                new WrittenEvent("vehicle_detail_gps_stream", "buffered"),
                new WrittenEvent("vehicle_detail_gps_stream", "live")
        );
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void fire(SseEmitter emitter, String event) {
        if (event.equals("error")) {
            ArgumentCaptor<Consumer<Throwable>> callback = ArgumentCaptor.forClass(Consumer.class);
            verify(emitter).onError(callback.capture());
            callback.getValue().accept(new IllegalStateException("disconnected"));
        } else {
            ArgumentCaptor<Runnable> callback = ArgumentCaptor.forClass(Runnable.class);
            if (event.equals("timeout")) {
                verify(emitter).onTimeout(callback.capture());
            } else {
                verify(emitter).onCompletion(callback.capture());
            }
            callback.getValue().run();
        }
    }

    private record WrittenEvent(String name, Object data) {
    }
}
