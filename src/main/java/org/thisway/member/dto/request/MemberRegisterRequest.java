package org.thisway.member.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.thisway.member.entity.Member;

public record MemberRegisterRequest(

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

    public Member toMember() {
        return Member.builder()
                .name(name)
                .email(email)
                .password(password)
                .phone(phone)
                .memo(memo)
                .build();
    }
}
