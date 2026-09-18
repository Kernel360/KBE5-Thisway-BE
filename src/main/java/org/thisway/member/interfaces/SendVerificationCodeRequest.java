package org.thisway.member.interfaces;

import jakarta.validation.constraints.NotBlank;

public record SendVerificationCodeRequest(@NotBlank String email) {

}
