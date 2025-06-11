package org.thisway.member.service.dto.input;

import lombok.Builder;
import org.thisway.member.entity.MemberRole;

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
