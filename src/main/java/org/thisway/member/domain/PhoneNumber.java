package org.thisway.member.domain;

import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.thisway.support.common.CustomException;
import org.thisway.support.common.ErrorCode;

@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class PhoneNumber {

    public static final String VALID_PHONE_NUMBER_REGEX = "^010\\d{8}$";

    private String value;

    public PhoneNumber(String value) {
        validateValue(value);
        this.value = value;
    }

    private void validateValue(String value) {
        if (value == null || !value.matches(VALID_PHONE_NUMBER_REGEX)) {
            throw new CustomException(ErrorCode.MEMBER_INVALID_PHONE_NUMBER);
        }
    }
}
