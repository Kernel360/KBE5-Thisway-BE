package org.thisway.member.application;

import lombok.RequiredArgsConstructor;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.thisway.support.common.CustomException;
import org.thisway.support.common.ErrorCode;
import org.thisway.company.domain.Company;
import org.thisway.company.domain.CompanyFixture;
import org.thisway.company.infrastructure.CompanyRepository;
import org.thisway.member.domain.Member;
import org.thisway.member.domain.MemberRole;
import org.thisway.member.infrastructure.MemberRepository;
import org.thisway.member.domain.MemberFixture;
import org.thisway.support.security.dto.request.MemberDetails;
import org.thisway.support.security.service.SecurityService;

import java.util.List;

import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.BDDMockito.given;

@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@RequiredArgsConstructor
@TestConstructor(autowireMode = AutowireMode.ALL)
class CompanyChefMemberServiceTest {

    private final CompanyChefMemberService companyChefMemberService;
    private final PasswordEncoder passwordEncoder;

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
        given(securityService.getCurrentMemberDetails())
                .willReturn(authenticatedMember);

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
        given(securityService.getCurrentMemberDetails())
                .willReturn(authenticatedMember);

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
        given(securityService.getCurrentMemberDetails())
                .willReturn(authenticatedMember);

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
        given(securityService.getCurrentMemberDetails())
                .willReturn(authenticatedMember);

        // when
        Pageable pageable = PageRequest.of(0, 10);
        var criteria = CompanyChefMemberSearchCriteria.builder()
                .memberName(null)
                .build();
        CompanyChefMembersOutput result = companyChefMemberService.getMembers(pageable, criteria);

        // then
        assertThat(result.pageInfo().numberOfElements()).isEqualTo(1);
        assertThat(result.pageInfo().size()).isEqualTo(10);
    }

    @Test
    @DisplayName("멤버를 등록할 수 있다.")
    void 멤버_등록_테스트() {
        //given
        Company companyForRegister = companyRepository.save(CompanyFixture.createCompany());

        CompanyChefMemberRegisterInput request = CompanyChefMemberRegisterInput.builder()
                .role(MemberRole.COMPANY_CHEF)
                .name("name")
                .email("email")
                .password("password")
                .phone("01012345678")
                .memo("memo")
                .build();

        Member authenticatedMember = MemberFixture.createMember(companyForRegister);
        given(securityService.getCurrentMember())
                .willReturn(authenticatedMember);

        // when
        companyChefMemberService.registerMember(request);

        // then
        Member registeredMember = memberRepository.findAll().getFirst();

        assertThat(registeredMember.getCompany().getId()).isEqualTo(companyForRegister.getId());
        assertThat(registeredMember.getRole()).isEqualTo(MemberRole.COMPANY_CHEF);
        assertThat(registeredMember.getName()).isEqualTo("name");
        assertThat(registeredMember.getEmail()).isEqualTo("email");
        assertThat(passwordEncoder.matches("password", registeredMember.getPassword())).isTrue();
        assertThat(registeredMember.getPhoneValue()).isEqualTo("01012345678");
        assertThat(registeredMember.getMemo()).isEqualTo("memo");
    }

    @Test
    @DisplayName("멤버를 등록할 때, 이미 존재하는 이메일의 등록 요청일 경우 예외가 발생한다.")
    void 멤버_등록_테스트_중복된_이메일() {
        //given
        String alreadyExistEmail = "email@email.com";
        Company company = companyRepository.save(CompanyFixture.createCompany());
        memberRepository.save(MemberFixture.createMemberWithEmail(company, alreadyExistEmail));

        CompanyChefMemberRegisterInput request = CompanyChefMemberRegisterInput.builder()
                .name("name")
                .email(alreadyExistEmail)
                .password("password")
                .phone("01012345678")
                .memo("memo")
                .build();

        Member authenticatedMember = MemberFixture.createMember(company);
        given(securityService.getCurrentMember())
                .willReturn(authenticatedMember);

        // when
        Throwable thrown = catchThrowable(() -> companyChefMemberService.registerMember(request));

        // then
        assertThat(thrown).isInstanceOf(CustomException.class);
        CustomException e = (CustomException) thrown;
        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBER_ALREADY_EXIST_BY_EMAIL);
    }

    @Test
    @DisplayName("멤버를 등록할 때, 허용하지 않는 생성 권한의 경우 예외를 던진다.")
    void 멤버_등록_테스트_허용하지_않는_생성_권한() {
        // given
        CompanyChefMemberRegisterInput request = CompanyChefMemberRegisterInput.builder()
                .name("name")
                .role(MemberRole.ADMIN)
                .email("email")
                .password("password")
                .phone("01012345678")
                .memo("memo")
                .build();

        // when
        Throwable thrown = catchThrowable(() -> companyChefMemberService.registerMember(request));

        // then
        assertThat(thrown).isInstanceOf(CustomException.class);
        CustomException e = (CustomException) thrown;
        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBER_REGISTER_DENIED);
    }

    @Test
    @DisplayName("멤버를 수정할 수 있다.")
    void 멤버_수정_테스트() {
        //given
        Company company = companyRepository.save(CompanyFixture.createCompany());
        Member member = memberRepository.save(
                Member.builder()
                        .company(company)
                        .role(MemberRole.COMPANY_CHEF)
                        .name("preUpdateName")
                        .email("pre@update.email")
                        .password("password")
                        .phone("01012345678")
                        .memo("preUpdatedMemo")
                        .build()
        );

        CompanyChefMemberUpdateInput request = CompanyChefMemberUpdateInput.builder()
                .id(member.getId())
                .name("updatedName")
                .email("updated@email.email")
                .phone("01087654321")
                .memo("updatedMemo")
                .build();

        MemberDetails authenticatedMember = MemberDetails.builder()
                .companyId(company.getId())
                .build();
        given(securityService.getCurrentMemberDetails())
                .willReturn(authenticatedMember);

        // when
        companyChefMemberService.updateMember(request);

        // then
        Member updatedMember = memberRepository.findAll().getFirst();

        Assertions.assertThat(updatedMember.getName()).isEqualTo("updatedName");
        Assertions.assertThat(updatedMember.getEmail()).isEqualTo("updated@email.email");
        Assertions.assertThat(updatedMember.getPhoneValue()).isEqualTo("01087654321");
        Assertions.assertThat(updatedMember.getMemo()).isEqualTo("updatedMemo");
    }

    @Test
    @DisplayName("멤버를 수정할 때, 없는 멤버 ID의 경우 예외를 발생시킨다.")
    void 멤버_수정_테스트_없는_멤버() {
        //given
        long invalidMemberId = 1L;
        CompanyChefMemberUpdateInput request = CompanyChefMemberUpdateInput.builder()
                .id(invalidMemberId)
                .name("name")
                .email("updated@email.email")
                .phone("01012345678")
                .memo("memo")
                .build();

        // when
        Throwable thrown = catchThrowable(() -> companyChefMemberService.updateMember(request));

        // then
        Assertions.assertThat(thrown).isInstanceOf(CustomException.class);
        CustomException e = (CustomException) thrown;
        Assertions.assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("멤버를 수정할 때, 이미 존재하는 이메일의 경우 예외가 발생한다.")
    void 멤버_수정_테스트_중복된_이메일() {
        //given
        String alreadyExistEmail = "already@exist.email";
        Company company = companyRepository.save(CompanyFixture.createCompany());
        memberRepository.save(MemberFixture.createMemberWithEmail(company, alreadyExistEmail));
        Member member = memberRepository.save(
                Member.builder()
                        .company(company)
                        .role(MemberRole.COMPANY_CHEF)
                        .name("preUpdateName")
                        .email("preUpdateEmail@email.com")
                        .password("password")
                        .phone("01012345678")
                        .memo("preUpdatedMemo")
                        .build()
        );

        CompanyChefMemberUpdateInput request = CompanyChefMemberUpdateInput.builder()
                .id(member.getId())
                .name("name")
                .email(alreadyExistEmail)
                .phone("01012345678")
                .memo("memo")
                .build();

        MemberDetails authenticatedMember = MemberDetails.builder()
                .companyId(company.getId())
                .build();
        given(securityService.getCurrentMemberDetails())
                .willReturn(authenticatedMember);

        // when
        Throwable thrown = catchThrowable(() -> companyChefMemberService.updateMember(request));

        // then
        Assertions.assertThat(thrown).isInstanceOf(CustomException.class);
        CustomException e = (CustomException) thrown;
        Assertions.assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBER_ALREADY_EXIST_BY_EMAIL);
    }

    @Test
    @DisplayName("멤버 정보를 수정할 때, ADMIN 일 경우 예외를 던진다.")
    void 멤버_수정_테스트_최고_관리자_이외의_수정() {
        //given
        Company company = companyRepository.save(CompanyFixture.createCompany());
        Member member = memberRepository.save(
                Member.builder()
                        .company(company)
                        .role(MemberRole.ADMIN)
                        .name("preUpdateName")
                        .email("pre@update.email")
                        .password("password")
                        .phone("01012345678")
                        .memo("preUpdatedMemo")
                        .build()
        );

        CompanyChefMemberUpdateInput request = CompanyChefMemberUpdateInput.builder()
                .id(member.getId())
                .name("updatedName")
                .email("updated@email.email")
                .phone("01087654321")
                .memo("updatedMemo")
                .build();

        MemberDetails authenticatedMember = MemberDetails.builder()
                .companyId(company.getId())
                .build();
        given(securityService.getCurrentMemberDetails())
                .willReturn(authenticatedMember);

        // when
        Throwable thrown = catchThrowable(() -> companyChefMemberService.updateMember(request));

        // then
        Assertions.assertThat(thrown).isInstanceOf(CustomException.class);
        CustomException e = (CustomException) thrown;
        Assertions.assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBER_ACCESS_DENIED);
    }

    @Test
    @DisplayName("멤버를 삭제할 수 있다.")
    void 멤버_삭제_테스트() {
        // given
        Company company = companyRepository.save(CompanyFixture.createCompany());
        Member member = memberRepository.save(MemberFixture.createMember(company, MemberRole.COMPANY_CHEF));

        MemberDetails authenticatedMember = MemberDetails.builder()
                .companyId(company.getId())
                .build();
        given(securityService.getCurrentMemberDetails())
                .willReturn(authenticatedMember);

        // when
        companyChefMemberService.deleteMember(member.getId());

        // then
        member = memberRepository.findById(member.getId()).get();
        Assertions.assertThat(member.isActive()).isFalse();
    }

    @Test
    @DisplayName("멤버를 삭제할 때, 없는 멤버 ID의 경우 예외를 발생시킨다.")
    void 멤버_삭제_테스트_없는_멤버() {
        //given
        long invalidMemberId = 1L;

        // when
        Throwable thrown = catchThrowable(() -> companyChefMemberService.deleteMember(invalidMemberId));

        // then
        Assertions.assertThat(thrown).isInstanceOf(CustomException.class);
        CustomException e = (CustomException) thrown;
        Assertions.assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("멤버 정보를 삭제할 때, ADMIN 정보일 경우 예외를 던진다.")
    void 멤버_삭제_테스트_최고_관리자_이외의_삭제() {
        //given
        Company company = companyRepository.save(CompanyFixture.createCompany());
        Member member = memberRepository.save(
                Member.builder()
                        .company(company)
                        .role(MemberRole.ADMIN)
                        .name("name")
                        .email("email")
                        .password("password")
                        .phone("01012345678")
                        .memo("memo")
                        .build()
        );

        MemberDetails authenticatedMember = MemberDetails.builder()
                .companyId(company.getId())
                .build();
        given(securityService.getCurrentMemberDetails())
                .willReturn(authenticatedMember);

        // when
        Throwable thrown = catchThrowable(() -> companyChefMemberService.deleteMember(member.getId()));

        // then
        Assertions.assertThat(thrown).isInstanceOf(CustomException.class);
        CustomException e = (CustomException) thrown;
        Assertions.assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBER_ACCESS_DENIED);
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
        given(securityService.getCurrentMemberDetails())
                .willReturn(memberDetails);

        // when
        CompanyChefMemberSummaryOutput summary = companyChefMemberService.summary();

        // then
        assertThat(summary.companyChefCount()).isEqualTo(1);
        assertThat(summary.companyAdminCount()).isEqualTo(0);
        assertThat(summary.memberCount()).isEqualTo(2);
    }
}
