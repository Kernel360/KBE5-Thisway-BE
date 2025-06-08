package org.thisway.member.dto;

import lombok.Builder;

@Builder
public record AdminMemberUpdateDto(
        long id,
        String name,
        String email,
        String phone,
        String memo
) {
}
