package org.thisway.member.dto.response;

import org.thisway.member.dto.AdminMemberDetailOutput;
import org.thisway.member.entity.MemberRole;

public record AdminMemberDetailResponse(
        Long id,
        String companyName,
        MemberRole role,
        String name,
        String email,
        String phone,
        String memo
) {

    public static AdminMemberDetailResponse from(AdminMemberDetailOutput member) {
        return new AdminMemberDetailResponse(
                member.id(),
                member.companyName(),
                member.role(),
                member.name(),
                member.email(),
                member.phone(),
                member.memo()
        );
    }
}
