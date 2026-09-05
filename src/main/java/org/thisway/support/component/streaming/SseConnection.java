package org.thisway.support.component.streaming;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Component
public class SseConnection {

    private static final long SSE_TIMEOUT = 30 * 60 * 1000L;
    static final int DEFAULT_MAX_BUFFERED_EVENTS = 256;

    public enum DeliveryResult {
        SENT,
        BUFFERED,
        MISSING,
        OVERFLOW,
        FAILED
    }

    private enum StreamState {
        INITIALIZING,
        LIVE,
        CLOSED
    }

    private record BufferedSseEvent(String eventName, Object data) {
    }

    @FunctionalInterface
    interface EventWriter {
        void send(SseEmitter emitter, String eventName, Object data) throws IOException;
    }

    public static class SseContext {
        final SseEmitter emitter;
        final LocalDateTime connectionTime;
        final Queue<BufferedSseEvent> bufferedLiveEvents = new ArrayDeque<>();
        StreamState state = StreamState.INITIALIZING;

        SseContext(SseEmitter emitter, LocalDateTime connectionTime) {
            this.emitter = emitter;
            this.connectionTime = connectionTime;
        }
    }

    private final Map<String, SseContext> emitters = new ConcurrentHashMap<>();
    private final Supplier<SseEmitter> emitterFactory;
    private final int maxBufferedEvents;
    private final EventWriter eventWriter;

    public SseConnection() {
        this(
                () -> new SseEmitter(SSE_TIMEOUT),
                DEFAULT_MAX_BUFFERED_EVENTS,
                (emitter, eventName, data) -> emitter.send(SseEmitter.event().name(eventName).data(data))
        );
    }

    SseConnection(Supplier<SseEmitter> emitterFactory) {
        this(
                emitterFactory,
                DEFAULT_MAX_BUFFERED_EVENTS,
                (emitter, eventName, data) -> emitter.send(SseEmitter.event().name(eventName).data(data))
        );
    }

    SseConnection(Supplier<SseEmitter> emitterFactory, int maxBufferedEvents, EventWriter eventWriter) {
        if (maxBufferedEvents < 1) {
            throw new IllegalArgumentException("maxBufferedEvents must be positive");
        }
        this.emitterFactory = emitterFactory;
        this.maxBufferedEvents = maxBufferedEvents;
        this.eventWriter = eventWriter;
    }

    public SseEmitter createSseEmitter(String key) {
        SseEmitter sseEmitter = emitterFactory.get();
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        SseContext context = new SseContext(sseEmitter, now);

        sseEmitter.onCompletion(() -> close(key, context));
        sseEmitter.onTimeout(() -> {
            close(key, context);
            sseEmitter.complete();
        });
        sseEmitter.onError(e -> {
            close(key, context);
            sseEmitter.complete();
        });

        SseContext previous = emitters.put(key, context);
        if (previous != null) {
            close(key, previous);
            previous.emitter.complete();
        }

        return sseEmitter;
    }

    public Optional<SseEmitter> get(String key) {
        return Optional.ofNullable(emitters.get(key))
                .map(context -> context.emitter);
    }

    public void remove(String key) {
        SseContext context = emitters.remove(key);
        if (context != null) {
            synchronized (context) {
                context.state = StreamState.CLOSED;
                context.bufferedLiveEvents.clear();
            }
        }
    }

    public Set<String> getAllKeys() {
        return emitters.keySet();
    }

    public Set<String> findKeysByPrefix(String prefix) {
        return emitters.keySet().stream()
                .filter(key -> key.startsWith(prefix.endsWith(":") ? prefix : prefix + ":"))
                .collect(Collectors.toSet());
    }

    public DeliveryResult sendLiveEvent(String key, String eventName, Object data) {
        SseContext context = emitters.get(key);
        if (context == null) {
            return DeliveryResult.MISSING;
        }

        synchronized (context) {
            if (emitters.get(key) != context || context.state == StreamState.CLOSED) {
                return DeliveryResult.MISSING;
            }
            if (context.state == StreamState.INITIALIZING) {
                if (context.bufferedLiveEvents.size() >= maxBufferedEvents) {
                    failForOverflow(key, context);
                    return DeliveryResult.OVERFLOW;
                }
                context.bufferedLiveEvents.add(new BufferedSseEvent(eventName, data));
                return DeliveryResult.BUFFERED;
            }

            return sendNow(key, context, eventName, data);
        }
    }

    public void markInitialChunkComplete(String key) {
        SseContext context = emitters.get(key);
        if (context == null) {
            return;
        }

        synchronized (context) {
            if (emitters.get(key) != context || context.state != StreamState.INITIALIZING) {
                return;
            }

            BufferedSseEvent event;
            while ((event = context.bufferedLiveEvents.poll()) != null) {
                if (sendNow(key, context, event.eventName(), event.data()) != DeliveryResult.SENT) {
                    return;
                }
            }
            context.state = StreamState.LIVE;
        }
    }

    private DeliveryResult sendNow(String key, SseContext context, String eventName, Object data) {
        try {
            eventWriter.send(context.emitter, eventName, data);
            return DeliveryResult.SENT;
        } catch (IOException | RuntimeException exception) {
            close(key, context);
            context.emitter.completeWithError(exception);
            return DeliveryResult.FAILED;
        }
    }

    private void failForOverflow(String key, SseContext context) {
        IllegalStateException cause = new IllegalStateException("SSE buffered event limit exceeded");
        close(key, context);
        context.emitter.completeWithError(cause);
    }

    private void close(String key, SseContext context) {
        synchronized (context) {
            context.state = StreamState.CLOSED;
            context.bufferedLiveEvents.clear();
            emitters.remove(key, context);
        }
    }
}
