package org.thisway.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        List<FieldError> fieldErrors = e.getBindingResult().getFieldErrors();

        String messageCode = fieldErrors.get(0).getDefaultMessage();
        ErrorCode errorCode = ErrorCode.fromCode(messageCode);
        if (errorCode == ErrorCode.SERVER_ERROR) {
            errorCode = ErrorCode.INVALID_INPUT_VALUE;
        }

        log.warn("클라이언트 요청 오류: {}", errorCode.getMessage(), e);

        return ApiErrorResponse.of(errorCode);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiErrorResponse> handleRuntimeException(RuntimeException e) {
        log.error("서버 내부 오류 발생", e);
        return ApiErrorResponse.of(ErrorCode.SERVER_ERROR);
    }

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiErrorResponse> handleCustomException(CustomException e) {
        ErrorCode errorCode = e.getErrorCode();
        HttpStatus status = errorCode.getStatus();
        String message = errorCode.getMessage();

        if (status.is5xxServerError()) {
            log.error("비즈니스 예외 발생: {}", message, e);
        } else if (status.is4xxClientError()) {
            log.warn("클라이언트 요청 오류: {}", message);
        } else {
            log.info("예외 발생: {}", message);
        }

        return ApiErrorResponse.of(errorCode);
    }
}
