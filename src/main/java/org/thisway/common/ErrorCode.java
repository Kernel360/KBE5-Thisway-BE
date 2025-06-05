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
    // 이메일 에러 x7xxx
    EMAIL_SEND_ERROR("07000", "이메일 발송에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),

    // DB 에러 x8xxx
    REDIS_STORE_ERROR("08000", "데이터를 저장하는데 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    REDIS_RETRIEVE_ERROR("08001", "저장된 데이터를 가져오는데 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),

    /* 비즈니스 에러 */
    // 업체 에러 x1xxx
    COMPANY_NOT_FOUND("11000", "회사 정보를 찾을 수 없습니다.", HttpStatus.BAD_REQUEST),
    COMPANY_ALREADY_EXIST("11001", "이미 존재하는 회사입니다.", HttpStatus.BAD_REQUEST),

    // 멤버 에러 x2xxx
    MEMBER_NOT_FOUND("12000","사용자 정보를 찾을 수 없습니다.", HttpStatus.BAD_REQUEST),
    MEMBER_ALREADY_EXIST_BY_EMAIL("12001","이미 등록된 이메일입니다.", HttpStatus.BAD_REQUEST),
    MEMBER_INVALID_PHONE_NUMBER("12003","유효하지 않은 핸드폰 번호입니다.", HttpStatus.BAD_REQUEST),

    // 인증 에러 x3xxx
    AUTH_INVALID_VERIFICATION_CODE("13000","잘못된 인증코드입니다.", HttpStatus.BAD_REQUEST),
    AUTH_UNAUTHENTICATED("13001","인증된 사용자가 아닙니다.", HttpStatus.UNAUTHORIZED),
    AUTH_INVALID_AUTHENTICATION("13002","잘못된 인증정보입니다.", HttpStatus.UNAUTHORIZED),

    // 차량 x4xxx
    VEHICLE_NOT_FOUND("14000", "차량 정보를 조회할 수 없습니다.", HttpStatus.BAD_REQUEST),
    VEHICLE_ALREADY_DELETED("14001", "이미 삭제된 차량입니다.", HttpStatus.BAD_REQUEST),
    VEHICLE_DUPLICATE_CAR_NUMBER("14002", "이미 등록된 차량 번호입니다", HttpStatus.BAD_REQUEST),
    VEHICLE_EMPTY_UPDATE_REQUEST("14003", "업데이트할 정보가 없습니다.", HttpStatus.BAD_REQUEST),

    // 에뮬레이터 x5xxx
    EMULATOR_NOT_FOUND("15000", "존재하지 않는 에뮬레이터입니다.", HttpStatus.BAD_REQUEST),

    // 페이지네이션 x6xxx
    PAGE_INVALID_PAGE_SIZE("16000", "페이지 크기는 최대 100개까지 가능합니다.", HttpStatus.BAD_REQUEST),
    PAGE_INVALID_SORT_PROPERTY("16001", "유효하지 않은 정렬 기준입니다.", HttpStatus.BAD_REQUEST),
    ;

    private final String code;
    private final String message;
    private final HttpStatus status;

    public int getStatusValue() {
        return status.value();
    }
}
