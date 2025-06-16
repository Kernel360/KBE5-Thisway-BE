package org.thisway.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        var fieldError = e.getBindingResult().getFieldErrors().stream().findFirst();
        ErrorCode errorCode = ErrorCode.SERVER_ERROR;

        if(fieldError.isPresent()){
            String code = fieldError.get().getDefaultMessage();
            errorCode = ErrorCode.fromCode(code);
        }

        return ResponseEntity.status(errorCode.getStatus())
            .body(new ApiErrorResponse(errorCode.getCode(), errorCode.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiErrorResponse> handleRuntimeException(RuntimeException e) {
        log.error(e.getMessage(), e);
        return ApiErrorResponse.of(ErrorCode.SERVER_ERROR);
    }

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiErrorResponse> handleCustomException(CustomException e) {
        HttpStatus status = e.getErrorCode().getStatus();
        if (status.is5xxServerError()) {
            log.error(e.getMessage(), e);
        } else if (status.is4xxClientError()) {
            log.warn(e.getMessage(), e);
        } else {
            log.info(e.getMessage(), e);
        }
        return ApiErrorResponse.of(e.getErrorCode());
    }
}
