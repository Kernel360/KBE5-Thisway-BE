package org.thisway.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record SendVerifyCodeRequest(@NotBlank String email) {

}
