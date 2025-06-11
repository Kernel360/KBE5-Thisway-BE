package org.thisway.member.controller.dto.request;

import jakarta.validation.constraints.NotBlank;

public record SendVerificationCodeRequest(@NotBlank String email) {

}
