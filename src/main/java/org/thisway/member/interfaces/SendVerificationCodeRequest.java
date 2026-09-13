package org.thisway.member.interfaces;

import jakarta.validation.constraints.NotBlank;

public record SendVerificationCodeRequest(@NotBlank(message = "12004")
        @jakarta.validation.constraints.Size(max = 257, message = "12004") String email) {

}
