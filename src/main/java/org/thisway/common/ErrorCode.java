package org.thisway.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Getter
public enum ErrorCode {

    SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버에러 입니다.")
    ;

    private final HttpStatus status;
    private final String message;

    public int getStatusValue() {
        return status.value();
    }
}
