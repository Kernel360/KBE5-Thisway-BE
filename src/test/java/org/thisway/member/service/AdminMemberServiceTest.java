package org.thisway.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import lombok.RequiredArgsConstructor;
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
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;
import org.thisway.company.entity.Company;
import org.thisway.company.repository.CompanyRepository;
import org.thisway.company.support.CompanyFixture;
import org.thisway.member.dto.AdminMemberRegisterDto;
import org.thisway.member.dto.MemberDto;
import org.thisway.member.dto.MembersDto;
import org.thisway.member.entity.Member;
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
    private final PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        memberRepository.deleteAll();
        companyRepository.deleteAll();
    }

    @Test
    @DisplayName("업체 최고 관리자 상세 정보를 조회할 수 있다")
    void 업체_최고_관리자_조회_성공() {
        // given
        Company company = companyRepository.save(CompanyFixture.createCompany());
        Member member = memberRepository.save(MemberFixture.createMember(company, MemberRole.COMPANY_CHEF));

        // when
        MemberDto result = adminMemberService.getMemberDetail(member.getId());

        // then
        assertThat(result.id()).isEqualTo(member.getId());
        assertThat(result.companyId()).isEqualTo(member.getCompany().getId());
        assertThat(result.role()).isEqualTo(member.getRole());
        assertThat(result.name()).isEqualTo(member.getName());
        assertThat(result.email()).isEqualTo(member.getEmail());
        assertThat(result.phone()).isEqualTo(member.getPhoneValue());
        assertThat(result.memo()).isEqualTo(member.getMemo());
    }

    @Test
    @DisplayName("업체 최고 관리자 상세 정보를 조회할 때, 없는 사용자일 경우 예외를 던진다.")
    void 업체_최고_관리자_조회_없는_사용자() {
        // given
        long invalidMemberId = 1L;
        Company company = companyRepository.save(CompanyFixture.createCompany());

        // when
        Throwable thrown = catchThrowable(() -> adminMemberService.getMemberDetail(invalidMemberId));

        // then
        assertThat(thrown).isInstanceOf(CustomException.class);
        CustomException e = (CustomException) thrown;
        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("업체 최고 관리자 상세 정보를 조회할 때, 최고 관리자 이외의 정보일 경우 예외를 던진다.")
    void 업체_최고_관리자_조회_최고_관리자_이외의_정보() {
        // given
        Company company = companyRepository.save(CompanyFixture.createCompany());
        Member member = memberRepository.save(MemberFixture.createMember(company, MemberRole.MEMBER));

        // when
        Throwable thrown = catchThrowable(() -> adminMemberService.getMemberDetail(member.getId()));

        // then
        assertThat(thrown).isInstanceOf(CustomException.class);
        CustomException e = (CustomException) thrown;
        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBER_ACCESS_DENIED);
    }

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

    @Test
    @DisplayName("멤버를 등록할 수 있다.")
    void 멤버_등록_테스트() {
        //given
        Company companyForRegister = companyRepository.save(CompanyFixture.createCompany());

        AdminMemberRegisterDto request = AdminMemberRegisterDto.builder()
                .companyId(companyForRegister.getId())
                .name("name")
                .email("email")
                .password("password")
                .phone("01012345678")
                .memo("memo")
                .build();

        // when
        adminMemberService.registerMember(request);

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
    @DisplayName("멤버를 등록할 때, 업체가 존재하지 않으면 예외를 던진다.")
    void 멤버_등록_테스트_존제하지_않는_업체() {
        //given
        Long invalidCompanyId = 1L;

        AdminMemberRegisterDto request = AdminMemberRegisterDto.builder()
                .companyId(invalidCompanyId)
                .name("name")
                .email("email")
                .password("password")
                .phone("01012345678")
                .memo("memo")
                .build();

        // when
        Throwable thrown = catchThrowable(() -> adminMemberService.registerMember(request));
        // then
        assertThat(thrown).isInstanceOf(CustomException.class);
        CustomException e = (CustomException) thrown;
        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMPANY_NOT_FOUND);
    }

    @Test
    @DisplayName("멤버를 등록할 때, 이미 존재하는 이메일의 등록 요청일 경우 예외가 발생한다.")
    void 멤버_등록_테스트_중복된_이메일() {
        //given
        String alreadyExistEmail = "email@email.com";
        Company company = companyRepository.save(CompanyFixture.createCompany());
        memberRepository.save(MemberFixture.createMemberWithEmail(company, alreadyExistEmail));

        AdminMemberRegisterDto request = AdminMemberRegisterDto.builder()
                .companyId(company.getId())
                .name("name")
                .email(alreadyExistEmail)
                .password("password")
                .phone("01012345678")
                .memo("memo")
                .build();

        // when
        Throwable thrown = catchThrowable(() -> adminMemberService.registerMember(request));
        // then
        assertThat(thrown).isInstanceOf(CustomException.class);
        CustomException e = (CustomException) thrown;
        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBER_ALREADY_EXIST_BY_EMAIL);
    }
}
