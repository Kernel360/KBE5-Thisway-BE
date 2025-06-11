package org.thisway.member.controller.dto.response;

import org.thisway.member.service.dto.output.CompanyChefMemberDetailOutput;
import org.thisway.member.entity.MemberRole;

public record CompanyChefMemberDetailResponse(
        Long id,
        MemberRole role,
        String name,
        String email,
        String phone,
        String memo
) {

    public static CompanyChefMemberDetailResponse from(CompanyChefMemberDetailOutput member) {
        return new CompanyChefMemberDetailResponse(
                member.id(),
                member.role(),
                member.name(),
                member.email(),
                member.phone(),
                member.memo()
        );
    }
}
