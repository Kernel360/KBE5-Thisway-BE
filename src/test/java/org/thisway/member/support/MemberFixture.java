package org.thisway.member.support;

import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.thisway.company.entity.Company;
import org.thisway.company.support.CompanyFixture;
import org.thisway.member.dto.request.MemberRegisterRequest;
import org.thisway.member.dto.response.MemberResponse;
import org.thisway.member.dto.response.MembersResponse;
import org.thisway.member.entity.Member;

public class MemberFixture {

    public static MemberRegisterRequest createMemberRegisterRequestWithCompanyId(long companyId) {
        return new MemberRegisterRequest(
                companyId,
                "홍길동",
                "hong@example.com",
                "Password123!",
                "01012345678",
                "가입 메모"
        );
    }

    public static MemberRegisterRequest createMemberRegisterRequestWithCompanyIdAndEmail(long companyId, String email) {
        return new MemberRegisterRequest(
                companyId,
                "홍길동",
                email,
                "Password123!",
                "01012345678",
                "가입 메모"
        );
    }

    public static Member createMember(Company company) {
        return Member.builder()
                .company(company)
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
                .name("홍길동")
                .email(email)
                .password("Password123!")
                .phone("01012345678")
                .memo("가입 메모")
                .build();
    }

    public static MemberResponse createMemberResponse(long id) {
        return new MemberResponse(
                1L,
                1L,
                "홍길동",
                "hong@example.com",
                "01012345678",
                "가입 메모"
        );
    }

    public static MembersResponse createMembersResponse(int size) {
        List<Member> members = new ArrayList<>();
        for (int i = 1; i <= size; i++) {
            members.add(createMember(CompanyFixture.createCompany()));
        }

        Page<Member> page = new PageImpl<>(
                members,
                PageRequest.of(0, size),
                size
        );

        return MembersResponse.from(page);
    }

    public static Member createInactiveMember(Company company) {
        Member member = createMember(company);
        member.delete();

        return member;
    }
}
