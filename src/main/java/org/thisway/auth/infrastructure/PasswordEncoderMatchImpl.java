package org.thisway.auth.infrastructure;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.thisway.auth.domain.PasswordEncoderMatch;
import org.thisway.support.common.CustomException;
import org.thisway.support.common.ErrorCode;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PasswordEncoderMatchImpl implements PasswordEncoderMatch {

    private final PasswordEncoder passwordEncoder;

    @Override
    public void assertMatches(CharSequence rawPassword, String encodedPassword) {
        if (!passwordEncoder.matches(rawPassword, encodedPassword))
            throw new CustomException(ErrorCode.AUTH_PASSWORD_NOT_MATCH);
    }
}
