package org.thisway.member.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.thisway.member.dto.AdminMemberRegisterInput;
import org.thisway.member.entity.MemberRole;

public record AdminMemberRegisterRequest(

        @NotNull
        Long companyId,

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

    public AdminMemberRegisterInput toMemberRegisterDto() {
        return AdminMemberRegisterInput.builder()
                .companyId(companyId)
                .role(role)
                .name(name)
                .email(email)
                .password(password)
                .phone(phone)
                .memo(memo)
                .build();
    }
}
