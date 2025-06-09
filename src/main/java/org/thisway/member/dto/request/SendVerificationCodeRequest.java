package org.thisway.member.dto.request;

import jakarta.validation.constraints.NotBlank;

public record SendVerificationCodeRequest(@NotBlank String email) {

}
