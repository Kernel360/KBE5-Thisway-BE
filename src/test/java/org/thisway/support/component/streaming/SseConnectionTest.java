package org.thisway.support.component.streaming;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

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
}
