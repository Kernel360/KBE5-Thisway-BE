package org.thisway.member.interfaces;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import org.thisway.member.application.CompanyChefMemberRegisterInput;
import org.thisway.member.domain.MemberRole;

public record CompanyChefMemberRegisterRequest(
        @NotNull
        MemberRole role,

        @NotBlank
        @Size(max = 255)
        String name,

        @NotBlank
        @Size(max = 255)
        String email,

        @NotBlank
        @Size(min = 8, max = 20, message = "12005")
        String password,

        @NotBlank
        String phone,

        @NotNull
        @Size(max = 255)
        String memo
) {

    public CompanyChefMemberRegisterInput toMemberRegisterInput() {
        return CompanyChefMemberRegisterInput.builder()
                .role(role)
                .name(name)
                .email(email)
                .password(password)
                .phone(phone)
                .memo(memo)
                .build();
    }
}
