package org.thisway.member.service.dto.input;

import lombok.Builder;

@Builder
public record CompanyChefMemberUpdateInput(
        long id,
        String name,
        String email,
        String phone,
        String memo
) {
}
