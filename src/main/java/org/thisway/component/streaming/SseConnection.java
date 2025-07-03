package org.thisway.component.streaming;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class SseConnection {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter createSseEmitter(String key) {
        SseEmitter sseEmitter = new SseEmitter(0L);
        emitters.put(key, sseEmitter);

        sseEmitter.onCompletion(() -> emitters.remove(key));
        sseEmitter.onTimeout(() -> emitters.remove(key));
        sseEmitter.onError(e -> emitters.remove(key));

        return sseEmitter;
    }

    public Optional<SseEmitter> get(String key) {
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
}
