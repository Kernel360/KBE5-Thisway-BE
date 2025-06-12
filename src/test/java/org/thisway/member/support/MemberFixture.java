package org.thisway.member.support;

import org.thisway.company.entity.Company;
import org.thisway.member.entity.Member;
import org.thisway.member.entity.MemberRole;

public class MemberFixture {

    public static Member createMember(Company company) {
        return Member.builder()
                .company(company)
                .role(MemberRole.MEMBER)
                .name("홍길동")
                .email("hong@example.com")
                .password("Password123!")
                .phone("01012345678")
                .memo("가입 메모")
                .build();
    }

    public static Member createMember(Company company, MemberRole role) {
        return Member.builder()
                .company(company)
                .role(role)
                .name("홍길동")
                .email("hong@example.com")
                .password("Password123!")
                .phone("01012345678")
                .memo("가입 메모")
                .build();
    }

    public static Member createMemberWithEmail(Company company, String email) {
        return Member.builder()
                .company(company)
                .role(MemberRole.MEMBER)
                .name("홍길동")
                .email(email)
                .password("Password123!")
                .phone("01012345678")
                .memo("가입 메모")
                .build();
    }

    public static Member createMemberWithEmail(Company company, MemberRole role, String email) {
        return Member.builder()
                .company(company)
                .role(role)
                .name("홍길동")
                .email(email)
                .password("Password123!")
                .phone("01012345678")
                .memo("가입 메모")
                .build();
    }

    public static Member createInactiveMember(Company company) {
        Member member = createMember(company);
        member.delete();

        return member;
    }
}
