package org.thisway.member.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PhoneNumberTest {

    @Test
    @DisplayName("유효한 휴대폰 번호는 정상적으로 생성된다")
    void 휴대폰_번호_생성_테스트_성공() {
        assertThatCode(() -> new PhoneNumber("01012345678"))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "010-1234-5678",
            "+82-10-1234-5678",
            "0212345678"
    })
    @DisplayName("유효하지 않은 휴대폰 번호 생성시 예외가 발생한다")
    void 휴대폰_번호_생성_테스트_유효하지_않은_번호(String invalidPhoneNumber) {
        CustomException exception = assertThrows(CustomException.class,
                () -> new PhoneNumber(invalidPhoneNumber));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MEMBER_INVALID_PHONE_NUMBER);
    }
}
