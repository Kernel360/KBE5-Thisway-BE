package org.thisway.member.dto;

import org.thisway.member.entity.Member;
import org.thisway.member.entity.MemberRole;

public record AdminMemberDetailOutput(
        Long id,
        String companyName,
        MemberRole role,
        String name,
        String email,
        String phone,
        String memo
) {

    public static AdminMemberDetailOutput from(Member member) {
        return new AdminMemberDetailOutput(
                member.getId(),
                member.getCompany().getName(),
                member.getRole(),
                member.getName(),
                member.getEmail(),
                member.getPhoneValue(),
                member.getMemo()
        );
    }
}
