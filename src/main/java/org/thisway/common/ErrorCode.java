package org.thisway.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Getter
public enum ErrorCode {

    /* 서버 에러 */
    SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버에러 입니다."),

    /* 비즈니스 에러 */
    // 회사
    COMPANY_NOT_FOUND(HttpStatus.NOT_FOUND, "회사 정보를 찾을 수 없습니다."),
    COMPANY_ALREADY_EXIST(HttpStatus.CONFLICT, "이미 존재하는 회사입니다."),

    // 멤버
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자 정보를 찾을 수 없습니다."),
    MEMBER_ALREADY_EXIST_BY_EMAIL(HttpStatus.CONFLICT, "이미 등록된 이메일입니다."),
    MEMBER_INVALID_PHONE_NUMBER(HttpStatus.BAD_REQUEST, "유효하지 않은 핸드폰 번호입니다."),
    INVALID_VERIFY_CODE(HttpStatus.BAD_REQUEST, "잘못된 인증코드입니다."),

    //차량
    VEHICLE_NOT_FOUND(HttpStatus.NOT_FOUND, "차량 정보를 조회할 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    public int getStatusValue() {
        return status.value();
    }
}
