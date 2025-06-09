package org.thisway.member.service;

import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.BDDMockito.given;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;
import org.thisway.company.entity.Company;
import org.thisway.company.repository.CompanyRepository;
import org.thisway.company.support.CompanyFixture;
import org.thisway.member.dto.CompanyChefMemberDetailOutput;
import org.thisway.member.dto.response.CompanyChefMembersOutput;
import org.thisway.member.entity.Member;
import org.thisway.member.entity.MemberRole;
import org.thisway.member.repository.MemberRepository;
import org.thisway.member.support.MemberFixture;
import org.thisway.security.dto.request.MemberDetails;
import org.thisway.security.service.SecurityService;

@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@RequiredArgsConstructor
@TestConstructor(autowireMode = AutowireMode.ALL)
class CompanyChefMemberServiceTest {

    private final CompanyChefMemberService companyChefMemberService;

    @MockitoBean
    private final SecurityService securityService;

    private final MemberRepository memberRepository;
    private final CompanyRepository companyRepository;

    @BeforeEach
    void setUp() {
        memberRepository.deleteAll();
        companyRepository.deleteAll();
    }

    @Test
    @DisplayName("멤버 상세 정보를 조회할 수 있다")
    void 멤버_조회_테스트_성공() {
        // given
        Company company = companyRepository.save(CompanyFixture.createCompany());
        Member member = memberRepository.save(MemberFixture.createMember(company, MemberRole.COMPANY_CHEF));

        MemberDetails authenticatedMember = MemberDetails.builder()
                .companyId(company.getId())
                .build();
        given(securityService.getCurrentMemberDetails()).willReturn(authenticatedMember);

        // when
        CompanyChefMemberDetailOutput result = companyChefMemberService.getMemberDetail(member.getId());

        // then
        assertThat(result.id()).isEqualTo(member.getId());
        assertThat(result.role()).isEqualTo(member.getRole());
        assertThat(result.name()).isEqualTo(member.getName());
        assertThat(result.email()).isEqualTo(member.getEmail());
        assertThat(result.phone()).isEqualTo(member.getPhoneValue());
        assertThat(result.memo()).isEqualTo(member.getMemo());
    }

    @Test
    @DisplayName("멤버 상세 정보를 조회할 때, 없는 사용자일 경우 예외를 던진다.")
    void 멤버_조회_테스트_없는_사용자() {
        // given
        long invalidMemberId = 1L;
        Company company = companyRepository.save(CompanyFixture.createCompany());

        // when
        Throwable thrown = catchThrowable(() -> companyChefMemberService.getMemberDetail(invalidMemberId));

        // then
        assertThat(thrown).isInstanceOf(CustomException.class);
        CustomException e = (CustomException) thrown;
        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("멤버 상세 정보를 조회할 때, ADMIN 정보일 경우 예외를 던진다.")
    void 멤버_조회_테스트_시스템_관리자_정보() {
        // given
        Company company = companyRepository.save(CompanyFixture.createCompany());
        Member member = memberRepository.save(MemberFixture.createMember(company, MemberRole.ADMIN));

        MemberDetails authenticatedMember = MemberDetails.builder()
                .companyId(company.getId())
                .build();
        given(securityService.getCurrentMemberDetails()).willReturn(authenticatedMember);

        // when
        Throwable thrown = catchThrowable(() -> companyChefMemberService.getMemberDetail(member.getId()));

        // then
        assertThat(thrown).isInstanceOf(CustomException.class);
        CustomException e = (CustomException) thrown;
        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBER_ACCESS_DENIED);
    }

    @Test
    @DisplayName("멤버 상세 정보를 조회할 때, 다른 업체 사용자에 대한 요청일 경우 예외를 던진다.")
    void 멤버_조회_테스트_다른_업체_사용자() {
        // given
        Company company = companyRepository.save(CompanyFixture.createCompany());
        Member member = memberRepository.save(MemberFixture.createMember(company, MemberRole.MEMBER));

        MemberDetails authenticatedMember = MemberDetails.builder()
                .companyId(company.getId() + 1)
                .build();
        given(securityService.getCurrentMemberDetails()).willReturn(authenticatedMember);

        // when
        Throwable thrown = catchThrowable(() -> companyChefMemberService.getMemberDetail(member.getId()));

        // then
        assertThat(thrown).isInstanceOf(CustomException.class);
        CustomException e = (CustomException) thrown;
        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBER_ACCESS_DENIED);
    }

    @Test
    @DisplayName("멤버 목록을 조회할 수 있다")
    void 멤버_목록_조회_테스트_성공() {
        // given
        Company company = companyRepository.save(CompanyFixture.createCompany());
        memberRepository.save(MemberFixture.createMember(company, MemberRole.COMPANY_CHEF));

        MemberDetails authenticatedMember = MemberDetails.builder()
                .companyId(company.getId())
                .build();
        given(securityService.getCurrentMemberDetails()).willReturn(authenticatedMember);

        // when
        Pageable pageable = PageRequest.of(0, 10);
        CompanyChefMembersOutput result = companyChefMemberService.getMembers(pageable);

        // then
        assertThat(result.pageInfo().numberOfElements()).isEqualTo(1);
        assertThat(result.pageInfo().size()).isEqualTo(10);
    }
}
