package org.thisway.member.dto.response;

import org.thisway.member.entity.Member;
import org.thisway.member.entity.MemberRole;

public record MemberResponse(
        Long id,
        Long companyId,
        MemberRole role,
        String name,
        String email,
        String phone,
        String memo
) {

    public static MemberResponse from(Member member) {
        return new MemberResponse(
                member.getId(),
                member.getCompany().getId(),
                member.getRole(),
                member.getName(),
                member.getEmail(),
                member.getPhoneValue(),
                member.getMemo()
        );
    }
}
