package org.thisway.auth.application;

import lombok.Builder;
import lombok.Value;

public class AuthCommand {

    @Value
    @Builder
    public static class LoginRequest {

        private final String email;
        private final String password;
    }

    @Value
    @Builder
    public static class LoginResponse {

        private final String accessToken;
        private final String refreshToken;
    }
}
