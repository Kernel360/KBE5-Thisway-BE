package org.thisway.support.security.dto.request;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String email,

        @NotBlank String password) {
}
