package org.thisway.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.thisway.company.entity.Company;
import org.thisway.company.repository.CompanyRepository;
import org.thisway.company.support.CompanyFixture;
import org.thisway.member.dto.MembersDto;
import org.thisway.member.entity.MemberRole;
import org.thisway.member.repository.MemberRepository;
import org.thisway.member.support.MemberFixture;

@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@RequiredArgsConstructor
@TestConstructor(autowireMode = AutowireMode.ALL)
class AdminMemberServiceTest {

    private final AdminMemberService adminMemberService;

    private final MemberRepository memberRepository;
    private final CompanyRepository companyRepository;

    @Test
    @DisplayName("COMPANY_CHEF 멤버 목록을 조회할 수 있다")
    void 멤버_목록_조회_성공() {
        // given
        Company company = companyRepository.save(CompanyFixture.createCompany());
        memberRepository.save(MemberFixture.createMember(company, MemberRole.COMPANY_CHEF));

        Pageable pageable = PageRequest.of(0, 10);

        // when
        MembersDto result = adminMemberService.getMembers(pageable);

        // then
        assertThat(result.pageInfo().numberOfElements()).isEqualTo(1);
        assertThat(result.pageInfo().size()).isEqualTo(10);
    }
}
