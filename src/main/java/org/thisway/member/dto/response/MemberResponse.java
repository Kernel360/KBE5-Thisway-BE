package org.thisway.member.dto.response;

import org.thisway.member.entity.Member;

public record MemberResponse(
        Long id,
        String name,
        String email,
        String password,
        String phone,
        String memo
) {

    public static MemberResponse from(Member member) {
        return new MemberResponse(
                member.getId(),
                member.getName(),
                member.getEmail(),
                member.getPassword(),
                member.getPhone(),
                member.getMemo()
        );
    }
}
