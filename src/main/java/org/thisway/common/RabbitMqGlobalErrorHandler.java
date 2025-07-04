package org.thisway.common;

import lombok.extern.log4j.Log4j2;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.stereotype.Component;
import org.springframework.util.ErrorHandler;

import java.nio.charset.StandardCharsets;

@Component
@Log4j2
public class RabbitMqGlobalErrorHandler implements ErrorHandler {

    @Override
    public void handleError(Throwable t) {
        if (t instanceof ListenerExecutionFailedException lefe) {
            Message message = lefe.getFailedMessage();
            String messageString = new String(message.getBody(), StandardCharsets.UTF_8);
            Throwable cause = lefe.getCause();

            if (cause instanceof CustomException customEx) {
                log.warn("클라이언트 메시지 예외: {}, payload: {}", customEx.getMessage(), messageString);
            } else {
                log.error("메시지 소비 중 서버 오류. payload: {}, exception: {}", messageString, cause.toString(), cause);
            }
        } else {
            log.error("알 수 없는 RabbitMQ 예외 발생", t);
        }
    }
}
