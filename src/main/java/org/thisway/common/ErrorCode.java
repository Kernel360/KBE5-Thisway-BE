package org.thisway.common;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
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
    MEMBER_NOT_FOUND("12000", "사용자 정보를 찾을 수 없습니다.", HttpStatus.BAD_REQUEST),
    MEMBER_ALREADY_EXIST_BY_EMAIL("12001", "이미 등록된 이메일입니다.", HttpStatus.BAD_REQUEST),
    MEMBER_INVALID_PHONE_NUMBER("12003", "유효하지 않은 핸드폰 번호입니다.", HttpStatus.BAD_REQUEST),
    MEMBER_INVALID_EMAIL("12004", "이메일 주소의 형식이 유효하지 않습니다.", HttpStatus.BAD_REQUEST),
    MEMBER_INVALID_PASSWORD("12005", "비밀번호가 유효하지 않습니다. 비밀번호는 알파벳, 숫자, 특수 문자(!@#$%^&*)를 각각 1개 이상 포함하여 8~20자로 구성되어야 합니다.",
            HttpStatus.BAD_REQUEST),
    MEMBER_ACCESS_DENIED("12006", "해당 멤버에 대한 접근 권한이 없습니다.", HttpStatus.FORBIDDEN),
    MEMBER_REGISTER_DENIED("12007", "해당 멤버에 대한 생성 권한이 없습니다.", HttpStatus.FORBIDDEN),


    // 인증 에러 x3xxx
    AUTH_INVALID_VERIFICATION_CODE("13000", "잘못된 인증코드입니다.", HttpStatus.BAD_REQUEST),
    AUTH_UNAUTHENTICATED("13001", "인증된 사용자가 아닙니다.", HttpStatus.UNAUTHORIZED),
    AUTH_INVALID_AUTHENTICATION("13002", "잘못된 인증정보입니다.", HttpStatus.UNAUTHORIZED),
    AUTH_UNAUTHORIZED("13003", "접근 권한이 없습니다.", HttpStatus.UNAUTHORIZED),

    // 차량 x4xxx
    VEHICLE_NOT_FOUND("14000", "차량 정보를 조회할 수 없습니다.", HttpStatus.BAD_REQUEST),
    VEHICLE_ALREADY_DELETED("14001", "이미 삭제된 차량입니다.", HttpStatus.BAD_REQUEST),
    VEHICLE_DUPLICATE_CAR_NUMBER("14002", "이미 등록된 차량 번호입니다", HttpStatus.BAD_REQUEST),
    VEHICLE_EMPTY_UPDATE_REQUEST("14003", "업데이트할 정보가 없습니다.", HttpStatus.BAD_REQUEST),
    VEHICLE_MODEL_ALREADY_EXISTS("14004", "이미 등록된 차량 모델입니다.", HttpStatus.BAD_REQUEST),
    VEHICLE_MODEL_NOT_FOUND("14005", "차량 모델을 찾을 수 없습니다.", HttpStatus.BAD_REQUEST),
    VEHICLE_NUMBER_NOT_VALID("14006", "차량 번호가 유효하지 않습니다.", HttpStatus.BAD_REQUEST),
    VEHICLE_INVALID_MANUFACTURER("14007", "제조사 입력은 필수입니다.", HttpStatus.BAD_REQUEST),
    VEHICLE_INVALID_MODEL_YEAR("14008", "연식 입력은 필수입니다.", HttpStatus.BAD_REQUEST),
    VEHICLE_INVALID_MODEL("14009", "차량모델 입력은 필수입니다.", HttpStatus.BAD_REQUEST),
    VEHICLE_POWER_OFF("14010", "현재 운행 중인 차량이 아닙니다.", HttpStatus.BAD_REQUEST),

    // 에뮬레이터 x5xxx
    EMULATOR_NOT_FOUND("15000", "존재하지 않는 에뮬레이터입니다.", HttpStatus.BAD_REQUEST),
    EMULATOR_ALREADY_EXIST("15001", "이미 존재하는 MDN입니다.", HttpStatus.BAD_REQUEST),
    EMULATOR_EMPTY_UPDATE_REQUEST("15002", "업데이트할 정보가 없습니다.", HttpStatus.BAD_REQUEST),

    // 페이지네이션 x6xxx
    PAGE_INVALID_PAGE_SIZE("16000", "페이지 크기는 최대 100개까지 가능합니다.", HttpStatus.BAD_REQUEST),
    PAGE_INVALID_SORT_PROPERTY("16001", "유효하지 않은 정렬 기준입니다.", HttpStatus.BAD_REQUEST),

    // 운행 로그 x7xxx
    TRIP_LOG_NOT_FOUND("17000", "해당하는 로그가 존재하지 않습니다.", HttpStatus.BAD_REQUEST),
    TRIP_LOG_ADDRESS_NOT_FOUND("17001", "주소를 찾을 수 없습니다.", HttpStatus.BAD_REQUEST),

    // 통계 로그 x8xxx
    STATISTICS_NOT_FOUND("18000", "통계 정보를 찾을 수 없습니다.", HttpStatus.BAD_REQUEST)
    ;

    private final String code;
    private final String message;
    private final HttpStatus status;

    private static final Map<String, ErrorCode> CODE_MAP = Arrays.stream(values())
        .collect(Collectors.toMap(ErrorCode::getCode, Function.identity()));

    public static ErrorCode fromCode(String code){
        return CODE_MAP.getOrDefault(code, SERVER_ERROR);
    }

    public int getStatusValue() {
        return status.value();
    }
}
