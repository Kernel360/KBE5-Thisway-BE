package org.thisway.member.interfaces.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.thisway.member.domain.MemberRole;

public class AdminMemberRequest {

    public record MemberRegisterRequest(
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
}
