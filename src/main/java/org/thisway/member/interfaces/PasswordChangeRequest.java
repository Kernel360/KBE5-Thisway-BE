package org.thisway.member.interfaces;

import jakarta.validation.constraints.NotBlank;

public record PasswordChangeRequest(
        @NotBlank(message = "12004") @jakarta.validation.constraints.Size(max = 257, message = "12004") String email,
        @NotBlank(message = "13000") @jakarta.validation.constraints.Pattern(regexp = "[0-9]{6}", message = "13000") String code,
        @NotBlank(message = "12005") @jakarta.validation.constraints.Size(min = 8, max = 20, message = "12005") String newPassword
) {

}
