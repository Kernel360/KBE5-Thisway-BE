package org.thisway.member.controller.dto.request;

import jakarta.validation.constraints.NotBlank;

public record PasswordChangeRequest(@NotBlank String email, @NotBlank String code, @NotBlank String newPassword) {

}
