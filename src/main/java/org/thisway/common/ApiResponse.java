package org.thisway.common;

import lombok.Value;
import org.springframework.http.HttpStatus;

@Value
public class ApiResponse<DATA> {

    int status;
    String message;
    DATA data;

    public static <DATA> ApiResponse<DATA> ok() {
        int status = HttpStatus.OK.value();

        return new ApiResponse<>(status, "", null);
    }

    public static <DATA> ApiResponse<DATA> ok(DATA data) {
        int status = HttpStatus.OK.value();

        return new ApiResponse<>(status, "", data);
    }

    public static <DATA> ApiResponse<DATA> error(ErrorCode errorCode) {
        int status = errorCode.getStatusValue();
        String message = errorCode.getMessage();

        return new ApiResponse<>(status, message, null);
    }
}
