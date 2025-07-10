package org.thisway.member.interfaces;

import org.thisway.member.application.CompanyChefMemberDetailOutput;
import org.thisway.member.domain.MemberRole;

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
