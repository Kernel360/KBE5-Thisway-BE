package org.thisway.auth.interfaces;

import org.thisway.auth.application.AuthCommand;
import org.thisway.auth.application.AuthInfo;

import jakarta.validation.constraints.NotBlank;

public class AuthDto {

    public record LoginRequest(

            @NotBlank
            String email,

            @NotBlank
            String password
    ){
        public AuthCommand.LoginRequest toCommand() {
            return AuthCommand.LoginRequest.builder()
                    .email(email)
                    .password(password)
                    .build();
        }
    }

    public record LoginResponse (
            String token,
            String refreshToken
    ) {

        public static LoginResponse from(AuthInfo.LoginResult info) {
            return new LoginResponse(info.getAccessToken(), info.getRefreshToken());
        }
    }
}
