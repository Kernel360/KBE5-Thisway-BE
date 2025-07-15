package org.thisway.member.domain;

public class MemberFixture {

    public static Member createMember(long companyId) {
        return Member.builder()
                .companyId(companyId)
                .role(MemberRole.MEMBER)
                .name("홍길동")
                .email("hong@example.com")
                .password("Password123!")
                .phone("01012345678")
                .memo("가입 메모")
                .build();
    }

    public static Member createMember(long companyId, MemberRole role) {
        return Member.builder()
                .companyId(companyId)
                .role(role)
                .name("홍길동")
                .email("hong@example.com")
                .password("Password123!")
                .phone("01012345678")
                .memo("가입 메모")
                .build();
    }

    public static Member createMemberWithEmail(long companyId, String email) {
        return Member.builder()
                .companyId(companyId)
                .role(MemberRole.MEMBER)
                .name("홍길동")
                .email(email)
                .password("Password123!")
                .phone("01012345678")
                .memo("가입 메모")
                .build();
    }

    public static Member createMemberWithEmail(long companyId, MemberRole role, String email) {
        return Member.builder()
                .companyId(companyId)
                .role(role)
                .name("홍길동")
                .email(email)
                .password("Password123!")
                .phone("01012345678")
                .memo("가입 메모")
                .build();
    }

    public static Member createInactiveMember(long companyId) {
        Member member = createMember(companyId);
        member.delete();

        return member;
    }
}
