package org.thisway.common;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiErrorResponse> handleRuntimeException() {
        return ApiErrorResponse.of(ErrorCode.SERVER_ERROR);
    }

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiErrorResponse> handleCustomException(CustomException e) {
        return ApiErrorResponse.of(e.getErrorCode());
    }
}
