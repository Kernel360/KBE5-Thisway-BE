package org.thisway.support.common;

import lombok.extern.log4j.Log4j2;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.stereotype.Component;
import org.springframework.util.ErrorHandler;

@Component
@Log4j2
public class RabbitMqGlobalErrorHandler implements ErrorHandler {

    @Override
    public void handleError(Throwable t) {
        if (t instanceof ListenerExecutionFailedException lefe) {
            Message message = lefe.getFailedMessage();
            int payloadSize = message.getBody().length;
            Throwable cause = lefe.getCause();

            if (cause instanceof CustomException customEx) {
                log.warn("클라이언트 메시지 예외: {}, payloadSize={}", customEx.getMessage(), payloadSize);
            } else {
                log.error("메시지 소비 중 서버 오류. payloadSize={}", payloadSize, cause);
            }
        } else {
            log.error("알 수 없는 RabbitMQ 예외 발생", t);
        }
    }
}
