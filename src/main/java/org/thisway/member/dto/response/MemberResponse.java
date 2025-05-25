package org.thisway.member.dto.response;

import org.thisway.member.entity.Member;

public record MemberResponse(
        Long id,
        Long companyId,
        String name,
        String email,
        String phone,
        String memo
) {

    public static MemberResponse from(Member member) {
        return new MemberResponse(
                member.getId(),
                member.getCompany().getId(),
                member.getName(),
                member.getEmail(),
                member.getPhoneValue(),
                member.getMemo()
        );
    }
}
