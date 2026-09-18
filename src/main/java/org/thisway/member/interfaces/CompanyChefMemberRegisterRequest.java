package org.thisway.member.interfaces;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.thisway.member.application.CompanyChefMemberRegisterInput;
import org.thisway.member.domain.MemberRole;

public record CompanyChefMemberRegisterRequest(
        @NotNull
        MemberRole role,

        @NotBlank
        String name,

        @NotBlank
        String email,

        @NotBlank
        String password,

        @NotBlank
        String phone,

        @NotNull
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
