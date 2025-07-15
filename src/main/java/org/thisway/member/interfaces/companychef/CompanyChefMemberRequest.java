package org.thisway.member.interfaces.companychef;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.thisway.member.domain.MemberRole;

public class CompanyChefMemberRequest {

    public record MemberRegisterRequest(
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
    }

    public record MemberUpdateRequest(
            @NotBlank
            String name,

            @NotBlank
            String email,

            @NotBlank
            String phone,

            @NotNull
            String memo
    ) {
    }

    public record MemberSearchRequest(String memberName) {
    }
}
