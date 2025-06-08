package org.thisway.member.dto;

import lombok.Builder;
import org.thisway.member.entity.MemberRole;

@Builder
public record AdminMemberRegisterDto(
        Long companyId,
        MemberRole role,
        String name,
        String email,
        String password,
        String phone,
        String memo
) {
}
