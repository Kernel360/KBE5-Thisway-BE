package org.thisway.auth.domain;

import lombok.Value;

@Value
public class AuthToken {

    private final String accessToken;
    private final String refreshToken;

    public static AuthToken of(String accessToken, String refreshToken) {
        return new AuthToken(accessToken, refreshToken);
    }
}
