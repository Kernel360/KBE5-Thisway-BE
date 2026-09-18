package org.thisway.support.component.streaming;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Component
public class SseConnection {

    private final Long SSE_TIMEOUT = 30 * 60 * 1000L;

    public static class SseContext {
        final SseEmitter emitter;
        final LocalDateTime connectionTime;
        final AtomicBoolean initialChunkCompleted = new AtomicBoolean(false);
        final Queue<Object> bufferedLiveData = new ConcurrentLinkedQueue<>();

        SseContext(SseEmitter emitter, LocalDateTime connectionTime) {
            this.emitter = emitter;
            this.connectionTime = connectionTime;
        }
    }

    private final Map<String, SseContext> emitters = new ConcurrentHashMap<>();

    public SseEmitter createSseEmitter(String key) {
        SseEmitter sseEmitter = new SseEmitter(SSE_TIMEOUT);
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        emitters.put(key, new SseContext(sseEmitter, now));

        sseEmitter.onCompletion(() -> emitters.remove(key));
        sseEmitter.onTimeout(() -> {
            sseEmitter.complete();
            emitters.remove(key);
        });
        sseEmitter.onError(e -> {
            sseEmitter.complete();
            emitters.remove(key);
        });

        return sseEmitter;
    }

    public Optional<SseEmitter> get(String key) {
        return Optional.ofNullable(emitters.get(key))
                .map(context -> context.emitter);
    }

    public Optional<SseContext> getContext(String key) {
        return Optional.ofNullable(emitters.get(key));
    }

    public void remove(String key) {
        emitters.remove(key);
    }

    public Set<String> getAllKeys() {
        return emitters.keySet();
    }

    public Set<String> findKeysByPrefix(String prefix) {
        return emitters.keySet().stream()
                .filter(key -> key.startsWith(prefix))
                .collect(Collectors.toSet());
    }

    public void markInitialChunkComplete(String key) {
        Optional.ofNullable(emitters.get(key)).ifPresent(context -> {
            context.initialChunkCompleted.set(true);

            while (!context.bufferedLiveData.isEmpty()) {
                Object data = context.bufferedLiveData.poll();
                try {
                    context.emitter.send(SseEmitter.event().name("live_gps").data(data));
                } catch (IOException e) {
                    context.emitter.complete();
                }
            }
        });
    }
}
