package org.thisway.member.dto;

import org.thisway.member.entity.Member;
import org.thisway.member.entity.MemberRole;

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
