package org.thisway.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record PasswordChangeRequest(
        @NotBlank
        String email,

        @NotBlank
        String code,

        @NotBlank
        String newPassword
) {

}
