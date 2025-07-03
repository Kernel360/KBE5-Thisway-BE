package org.thisway.member.controller.dto.request;

import org.thisway.logging.masking.MaskingData;

import jakarta.validation.constraints.NotBlank;

public record PasswordChangeRequest(
    @NotBlank String email,
    @NotBlank String code,
    @MaskingData @NotBlank String newPassword
) {

}
