package org.thisway.member.application;

import org.thisway.member.domain.Member;
import org.thisway.member.domain.MemberRole;

public record CompanyChefMemberDetailOutput(
        Long id,
        MemberRole role,
        String name,
        String email,
        String phone,
        String memo
) {

    public static CompanyChefMemberDetailOutput from(Member member) {
        return new CompanyChefMemberDetailOutput(
                member.getId(),
                member.getRole(),
                member.getName(),
                member.getEmail(),
                member.getPhoneValue(),
                member.getMemo()
        );
    }
}
