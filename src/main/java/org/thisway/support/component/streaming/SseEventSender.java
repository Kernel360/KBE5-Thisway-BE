package org.thisway.support.component.streaming;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.thisway.support.common.CustomException;
import org.thisway.support.common.ErrorCode;

import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class SseEventSender {

    private final SseConnection sseConnection;

    public void send(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(data));
        } catch (Exception e) {
            emitter.complete();
            throw new CustomException(ErrorCode.SSE_SEND_ERROR);
        }
    }

    public void sendToPrefix(String prefix, String eventName, Object data) {
        Set<String> keys = sseConnection.findKeysByPrefix(prefix);
        int failCount = 0;

        for (String key : keys) {
            SseConnection.DeliveryResult result = sendLiveDataWithBuffering(key, eventName, data);
            if (result == SseConnection.DeliveryResult.FAILED
                    || result == SseConnection.DeliveryResult.OVERFLOW) {
                failCount++;
                log.warn("SSE 전송 실패. event: {}, result: {}", eventName, result);
            }
        }

        log.info("SSE 전송 완료. 총 {}건 중 {}건 실패", keys.size(), failCount);
    }

    public SseConnection.DeliveryResult sendLiveDataWithBuffering(String key, String eventName, Object data) {
        return sseConnection.sendLiveEvent(key, eventName, data);
    }
}
