package org.thisway.member.application;

import lombok.Builder;

@Builder
public record AdminMemberUpdateInput(
        long id,
        String name,
        String email,
        String phone,
        String memo
) {
}
