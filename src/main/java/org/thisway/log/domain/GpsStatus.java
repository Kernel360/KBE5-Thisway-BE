package org.thisway.log.domain;

import lombok.Getter;

@Getter
public enum GpsStatus {
    NORMAL("A", "정상"),
    ABNORMAL("V", "비정상"),
    NOT_INSTALLED("0", "미장착"),
    ABNORMAL_ON_IGNITION("P", "시동 OFF시 비정상");

    private final String code;
    private final String description;

    GpsStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static GpsStatus fromCode(String code) {
        for (GpsStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("알 수 없는 GPS 상태 코드: " + code);
    }

}
