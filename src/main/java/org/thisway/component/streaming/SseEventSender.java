package org.thisway.component.streaming;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class SseEventSender {

    private final SseConnection sseConnection;

    public <T> void send(SseEmitter emitter, String eventName, T data) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(data));
        } catch (Exception e) {
            emitter.complete();
            throw new CustomException(ErrorCode.SSE_SEND_ERROR);
        }
    }

    public <T> void sendToPrefix(String prefix, String eventName, T data) {
        Set<String> keys = sseConnection.findKeysByPrefix(prefix);

        for (String key : keys) {
            sseConnection.get(key).ifPresent(
                            emitter -> send(emitter, eventName, data)
            );
        }
    }
}
