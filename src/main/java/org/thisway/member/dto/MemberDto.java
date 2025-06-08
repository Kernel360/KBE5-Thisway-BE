package org.thisway.member.dto;

import org.thisway.member.entity.Member;
import org.thisway.member.entity.MemberRole;

public record MemberDto(
        Long id,
        Long companyId,
        MemberRole role,
        String name,
        String email,
        String phone,
        String memo
) {

    public static MemberDto from(Member member) {
        return new MemberDto(
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
