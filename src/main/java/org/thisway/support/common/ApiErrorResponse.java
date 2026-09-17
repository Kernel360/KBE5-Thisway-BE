package org.thisway.support.common;

import org.springframework.http.ResponseEntity;

public record ApiErrorResponse(String code, String message) {

    public static ResponseEntity<ApiErrorResponse> of(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.getStatus())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(new ApiErrorResponse(errorCode.getCode(), errorCode.getMessage()));
    }
}
