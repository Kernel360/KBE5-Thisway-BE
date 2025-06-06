package org.thisway.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;
import org.thisway.common.PageInfo;
import org.thisway.company.entity.Company;
import org.thisway.company.repository.CompanyRepository;
import org.thisway.company.support.CompanyFixture;
import org.thisway.member.dto.MemberSummaryDto;
import org.thisway.member.dto.request.MemberRegisterRequest;
import org.thisway.member.dto.response.MemberResponse;
import org.thisway.member.dto.response.MembersResponse;
import org.thisway.member.entity.Member;
import org.thisway.member.entity.MemberRole;
import org.thisway.member.repository.MemberRepository;
import org.thisway.member.support.MemberFixture;
import org.thisway.security.dto.request.MemberDetails;
import org.thisway.security.service.SecurityService;

@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@RequiredArgsConstructor
@TestConstructor(autowireMode = AutowireMode.ALL)
class MemberServiceTest {

    private final MemberService memberService;

    @MockitoBean
    private final SecurityService securityService;

    private final MemberRepository memberRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("멤버가 정상적으로 조회된다.")
    void 멤버_조회_테스트_성공() {
        // given
        Company company = companyRepository.save(CompanyFixture.createCompany());
        Member member = MemberFixture.createMember(company);

        // when
        memberRepository.save(member);
        MemberResponse memberResponse = memberService.getMemberDetail(member.getId());

        // then
        assertThat(memberResponse.id()).isEqualTo(member.getId());
        assertThat(memberResponse.email()).isEqualTo(member.getEmail());
        assertThat(memberResponse.phone()).isEqualTo(member.getPhoneValue());
    }

    @Test
    @DisplayName("없는 멤버를 조회하려 하면 member not found exception이 발생한다.")
    void 멤버_조회_테스트_없는_멤버() {
        // given
        Company company = companyRepository.save(CompanyFixture.createCompany());
        Member member = MemberFixture.createMember(company);

        // when
        memberRepository.save(member);
        CustomException e = assertThrows(
                CustomException.class,
                () -> memberService.getMemberDetail(member.getId() + 1));

        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
        // then
    }

    @Test
    @DisplayName("멤버가 페이징 정보에 맞게 정상적으로 조회된다")
    void 멤버_페이징_조회_테스트_성공() {
        // given
        Company company = companyRepository.save(CompanyFixture.createCompany());
        List<Member> members = List.of(
                MemberFixture.createMemberWithEmail(company, "hong1@example.com"),
                MemberFixture.createMemberWithEmail(company, "hong2@example.com"),
                MemberFixture.createMemberWithEmail(company, "hong3@example.com"),
                MemberFixture.createMemberWithEmail(company, "hong4@example.com")
        );
        memberRepository.saveAll(members);

        Member admin = MemberFixture.createMember(company, MemberRole.ADMIN);
        given(securityService.getCurrentMember()).willReturn(admin);

        // when
        MembersResponse membersResponse = memberService.getMembers(PageRequest.of(0, 2));

        // then
        PageInfo pageInfo = membersResponse.pageInfo();

        assertThat(pageInfo.totalElements()).isEqualTo(4);
        assertThat(pageInfo.numberOfElements()).isEqualTo(2);
        assertThat(pageInfo.totalPages()).isEqualTo(2);
        assertThat(pageInfo.currentPage()).isEqualTo(0);
        assertThat(pageInfo.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("업체 최고 관리자가 멤버가 페이징 조회할 경우, admin을 제외한 멤버 정보가 정상적으로 조회된다")
    void 멤버_페이징_조회_권한_필터_테스트_성공_업체_최고_관리자() {
        // given
        Company company = companyRepository.save(CompanyFixture.createCompany());
        List<Member> members = List.of(
                MemberFixture.createMemberWithEmail(company, MemberRole.ADMIN, "hong1@example.com"),
                MemberFixture.createMemberWithEmail(company, "hong2@example.com"),
                MemberFixture.createMemberWithEmail(company, "hong3@example.com"),
                MemberFixture.createMemberWithEmail(company, "hong4@example.com")
        );
        memberRepository.saveAll(members);

        Member companyAdmin = MemberFixture.createMember(company, MemberRole.COMPANY_ADMIN);
        given(securityService.getCurrentMember()).willReturn(companyAdmin);

        // when
        MembersResponse membersResponse = memberService.getMembers(PageRequest.of(0, 2));

        // then
        PageInfo pageInfo = membersResponse.pageInfo();

        assertThat(pageInfo.totalElements()).isEqualTo(3);
        assertThat(pageInfo.numberOfElements()).isEqualTo(2);
        assertThat(pageInfo.totalPages()).isEqualTo(2);
        assertThat(pageInfo.currentPage()).isEqualTo(0);
        assertThat(pageInfo.size()).isEqualTo(2);
    }


    @Test
    @DisplayName("업체 최고 관리자가 멤버가 페이징 조회할 경우, 본인의 업체만 조회한다.")
    void 멤버_페이징_조회_업체_필터_테스트_성공_업체_최고_관리자() {
        // given
        Company company = companyRepository.save(CompanyFixture.createCompany());
        Company otherCompany = companyRepository.save(CompanyFixture.createCompany());
        List<Member> members = List.of(
                MemberFixture.createMemberWithEmail(otherCompany, "hong1@example.com"),
                MemberFixture.createMemberWithEmail(company, "hong2@example.com"),
                MemberFixture.createMemberWithEmail(company, "hong3@example.com"),
                MemberFixture.createMemberWithEmail(company, "hong4@example.com")
        );
        memberRepository.saveAll(members);

        Member companyAdmin = MemberFixture.createMember(company, MemberRole.COMPANY_ADMIN);
        given(securityService.getCurrentMember()).willReturn(companyAdmin);

        // when
        MembersResponse membersResponse = memberService.getMembers(PageRequest.of(0, 2));

        // then
        PageInfo pageInfo = membersResponse.pageInfo();

        assertThat(pageInfo.totalElements()).isEqualTo(3);
        assertThat(pageInfo.numberOfElements()).isEqualTo(2);
        assertThat(pageInfo.totalPages()).isEqualTo(2);
        assertThat(pageInfo.currentPage()).isEqualTo(0);
        assertThat(pageInfo.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("멤버 등록이 정상적으로 등록된다.")
    void 멤버_등록_테스트_성공() {
        // given
        Company company = companyRepository.save(CompanyFixture.createCompany());
        MemberRegisterRequest request = MemberFixture.createMemberRegisterRequestWithCompanyId(company.getId());

        // when
        memberService.registerMember(request);

        // then
        List<Member> allMember = memberRepository.findAll();
        Member savedMember = allMember.getFirst();

        assertThat(allMember).hasSize(1);
        assertThat(savedMember.getId()).isNotNull();
        assertThat(savedMember.getEmail()).isEqualTo(request.email());
        assertThat(passwordEncoder.matches(request.password(), savedMember.getPassword())).isTrue();
        assertThat(savedMember.getPhoneValue()).isEqualTo(request.phone());
    }

    @Test
    @DisplayName("멤버_등록시_존재하는_이메일의_회원일_경우_예외가_발생한다")
    void 멤버_등록_테스트_존재하는_이메일() {
        // given
        String email = "hong@example.com";
        Company company = companyRepository.save(CompanyFixture.createCompany());
        MemberRegisterRequest request = MemberFixture.createMemberRegisterRequestWithCompanyIdAndEmail(company.getId(),
                email);

        // when & then
        memberRepository.save(MemberFixture.createMemberWithEmail(company, email));
        CustomException e = assertThrows(CustomException.class, () -> memberService.registerMember(request));

        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBER_ALREADY_EXIST_BY_EMAIL);
    }

    @Test
    @DisplayName("멤버가 정상적으로 삭제된다.")
    void 멤버_삭제_테스트_성공() {
        // given
        Company company = companyRepository.save(CompanyFixture.createCompany());
        Member member = MemberFixture.createMember(company);

        // when
        memberRepository.save(member);

        memberService.deleteMember(member.getId());

        // then
        Member deletedMember = memberRepository.findById(member.getId()).orElse(null);
        assertThat(deletedMember).isNotNull();
        assertThat(deletedMember.isActive()).isFalse();
    }

    @Test
    @DisplayName("없는 멤버를 삭제하려 하면 member not found exception이 발생한다.")
    void 멤버_삭제_테스트_없는_멤버() {
        // when & then
        CustomException e = assertThrows(CustomException.class, () -> memberService.deleteMember(1L));

        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("멤버 요약을 성공적으로 조회할 수 있다.")
    void 멤버_요약_조회_테스트_성공() {
        // given
        Company company = companyRepository.save(CompanyFixture.createCompany());
        List<Member> members = List.of(
                MemberFixture.createMemberWithEmail(company, MemberRole.COMPANY_CHEF, "email1@email.com"),
                MemberFixture.createMemberWithEmail(company, MemberRole.MEMBER, "email2@email.com"),
                MemberFixture.createMemberWithEmail(company, MemberRole.MEMBER, "email3@email.com")
        );
        memberRepository.saveAll(members);

        MemberDetails memberDetails = MemberDetails.builder()
                .companyId(company.getId())
                .role(MemberRole.COMPANY_CHEF)
                .build();
        given(securityService.getCurrentMemberDetails()).willReturn(memberDetails);

        // when
        MemberSummaryDto summaryDto = memberService.summary();

        // then
        assertThat(summaryDto.companyChefCount()).isEqualTo(1);
        assertThat(summaryDto.companyAdminCount()).isEqualTo(0);
        assertThat(summaryDto.memberCount()).isEqualTo(2);
    }
}
