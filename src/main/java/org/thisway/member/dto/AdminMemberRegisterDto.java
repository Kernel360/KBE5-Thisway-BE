package org.thisway.member.dto;

import lombok.Builder;

@Builder
public record AdminMemberRegisterDto(
        Long companyId,
        String name,
        String email,
        String password,
        String phone,
        String memo
) {
}
