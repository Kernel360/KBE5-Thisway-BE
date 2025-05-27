package org.thisway.member.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;

class PhoneNumberTest {

    @Test
    @DisplayName("유효한 휴대폰 번호는 정상적으로 생성된다")
    void 휴대폰_번호_생성_테스트_성공() {
        assertThatCode(() -> new PhoneNumber("01012345678"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("유효하지 않은 휴대폰 번호 생성시 예외가 발생한다")
    void 휴대폰_번호_생성_테스트_유효하지_않은_번호() {
        CustomException e1 = assertThrows(CustomException.class,
                () -> new PhoneNumber("010-1234-5678"));
        CustomException e2 = assertThrows(CustomException.class,
                () -> new PhoneNumber("+82-10-1234-5678"));
        CustomException e3 = assertThrows(CustomException.class,
                () -> new PhoneNumber("0212345678"));

        assertThat(e1.getErrorCode()).isEqualTo(ErrorCode.MEMBER_INVALID_PHONE_NUMBER);
        assertThat(e2.getErrorCode()).isEqualTo(ErrorCode.MEMBER_INVALID_PHONE_NUMBER);
        assertThat(e3.getErrorCode()).isEqualTo(ErrorCode.MEMBER_INVALID_PHONE_NUMBER);
    }
}
