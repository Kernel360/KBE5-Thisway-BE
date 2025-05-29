package org.thisway.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/*
 * 서버 에러: 0xxxx
 * request 에러: 1xxxx
 */
@RequiredArgsConstructor
@Getter
public enum ErrorCode {

    /* 서버 에러 */
    SERVER_ERROR("00000", "서버에러 입니다.", HttpStatus.INTERNAL_SERVER_ERROR),

    /* 비즈니스 에러 */
    // 업체 에러: x1xxx
    COMPANY_NOT_FOUND("11000", "회사 정보를 찾을 수 없습니다.", HttpStatus.BAD_REQUEST),
    COMPANY_ALREADY_EXIST("11001", "이미 존재하는 회사입니다.", HttpStatus.BAD_REQUEST),

    // 멤버 에러 x2xxx
    MEMBER_NOT_FOUND( "12000","사용자 정보를 찾을 수 없습니다.", HttpStatus.BAD_REQUEST),
    MEMBER_ALREADY_EXIST_BY_EMAIL( "12001","이미 등록된 이메일입니다.", HttpStatus.BAD_REQUEST),
    MEMBER_INVALID_PHONE_NUMBER( "12003","유효하지 않은 핸드폰 번호입니다.", HttpStatus.BAD_REQUEST),

    // 인증 에러 x3xxx
    AUTH_INVALID_VERIFY_CODE( "13000","잘못된 인증코드입니다.", HttpStatus.BAD_REQUEST),
    ;

    private final String code;
    private final String message;
    private final HttpStatus status;

    public int getStatusValue() {
        return status.value();
    }
}
