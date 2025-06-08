package org.thisway.member.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.thisway.member.dto.AdminMemberRegisterDto;

public record AdminMemberRegisterRequest(

        @NotNull
        Long companyId,

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

    public AdminMemberRegisterDto toMemberRegisterDto() {
        return AdminMemberRegisterDto.builder()
                .companyId(companyId)
                .name(name)
                .email(email)
                .password(password)
                .phone(phone)
                .memo(memo)
                .build();
    }
}
