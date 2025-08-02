package org.thisway.auth.application;

import org.thisway.auth.domain.AuthToken;

import lombok.Builder;
import lombok.Value;

public class AuthInfo {

    @Value
    @Builder
    public static class LoginResult {

        private final String accessToken;
        private final String refreshToken;

        public static LoginResult from(AuthToken tokens) {
            return LoginResult.builder()
                    .accessToken(tokens.getAccessToken())
                    .refreshToken(tokens.getRefreshToken())
                    .build();
        }
    }
}
