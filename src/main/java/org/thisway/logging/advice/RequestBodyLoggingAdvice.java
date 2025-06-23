package org.thisway.logging.advice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import org.thisway.logging.constant.MdcKeys;

import java.lang.reflect.Type;

@ControllerAdvice
@Slf4j
public class RequestBodyLoggingAdvice extends RequestBodyAdviceAdapter {

    private final ObjectMapper objectMapper;

    public RequestBodyLoggingAdvice(@Qualifier("maskingObjectMapper") ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(
            MethodParameter methodParameter, Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType
    ) {
        return true;
    }

    public Object afterBodyRead(
            Object body,
            HttpInputMessage inputMessage,
            MethodParameter parameter,
            Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType
    ) {
        try {
            String bodyStr = objectMapper.writeValueAsString(body);
            MDC.put(MdcKeys.REQUEST_BODY, bodyStr);
        } catch (JsonProcessingException e) {
            log.warn("로깅 Request Body 직렬화를 실패했습니다.", e);
        }

        return super.afterBodyRead(body, inputMessage, parameter, targetType, converterType);
    }
}
