package org.thisway.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Getter
public enum ErrorCode {

    /* 서버 에러 */
    SERVER_ERROR("SERVER_001", "서버에러 입니다.", HttpStatus.INTERNAL_SERVER_ERROR),

    /* 비즈니스 에러 */
    // 회사
    COMPANY_NOT_FOUND("COMPANY_001", "회사 정보를 찾을 수 없습니다.", HttpStatus.BAD_REQUEST),
    COMPANY_ALREADY_EXIST("COMPANY_002", "이미 존재하는 회사입니다.", HttpStatus.BAD_REQUEST),

    // 멤버
    MEMBER_NOT_FOUND( "MEMBER_001","사용자 정보를 찾을 수 없습니다.", HttpStatus.BAD_REQUEST),
    MEMBER_ALREADY_EXIST_BY_EMAIL( "MEMBER_002","이미 등록된 이메일입니다.", HttpStatus.BAD_REQUEST),
    MEMBER_INVALID_PHONE_NUMBER( "MEMBER_003","유효하지 않은 핸드폰 번호입니다.", HttpStatus.BAD_REQUEST),

    // 인증
    AUTH_INVALID_VERIFY_CODE( "AUTH_001","잘못된 인증코드입니다.", HttpStatus.BAD_REQUEST),
    ;

    private final String code;
    private final String message;
    private final HttpStatus status;

    public int getStatusValue() {
        return status.value();
    }
}
