package org.thisway.member.application;

import lombok.Builder;
import org.thisway.member.domain.MemberRole;

@Builder
public record AdminMemberRegisterInput(
        Long companyId,
        MemberRole role,
        String name,
        String email,
        String password,
        String phone,
        String memo
) {
}
