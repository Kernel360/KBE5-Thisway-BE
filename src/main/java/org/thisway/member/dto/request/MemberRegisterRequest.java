package org.thisway.member.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.thisway.company.entity.Company;
import org.thisway.member.entity.Member;
import org.thisway.member.entity.MemberRole;

public record MemberRegisterRequest(

        @NotNull Long companyId,

        @NotNull MemberRole role,

        @NotBlank String name,

        @NotBlank String email,

        @NotBlank String password,

        @NotBlank String phone,

        @NotNull String memo) {

    public Member toMember(Company company, String encodedPassword) {
        return Member.builder()
                .company(company)
                .role(role)
                .name(name)
                .email(email)
                .password(encodedPassword)
                .phone(phone)
                .memo(memo)
                .build();
    }
}
