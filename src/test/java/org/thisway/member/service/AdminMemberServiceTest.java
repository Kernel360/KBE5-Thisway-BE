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
import org.thisway.member.dto.AdminMemberRegisterInput;
import org.thisway.member.dto.AdminMemberUpdateInput;
import org.thisway.member.dto.AdminMemberDetailOutput;
import org.thisway.member.dto.AdminMembersOutput;
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
    @DisplayName("멤버 상세 정보를 조회할 수 있다")
    void 멤버_조회_테스트_성공() {
        // given
        Company company = companyRepository.save(CompanyFixture.createCompany());
        Member member = memberRepository.save(MemberFixture.createMember(company, MemberRole.COMPANY_CHEF));

        // when
        AdminMemberDetailOutput result = adminMemberService.getMemberDetail(member.getId());

        // then
        assertThat(result.id()).isEqualTo(member.getId());
        assertThat(result.companyName()).isEqualTo(member.getCompany().getName());
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
        Throwable thrown = catchThrowable(() -> adminMemberService.getMemberDetail(invalidMemberId));

        // then
        assertThat(thrown).isInstanceOf(CustomException.class);
        CustomException e = (CustomException) thrown;
        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("멤버 상세 정보를 조회할 때, 최고 관리자 이외의 정보일 경우 예외를 던진다.")
    void 멤버_조회_테스트_최고_관리자_이외의_정보() {
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
    @DisplayName("멤버 목록을 조회할 수 있다")
    void 멤버_목록_조회_테스트_성공() {
        // given
        Company company = companyRepository.save(CompanyFixture.createCompany());
        memberRepository.save(MemberFixture.createMember(company, MemberRole.COMPANY_CHEF));

        Pageable pageable = PageRequest.of(0, 10);

        // when
        AdminMembersOutput result = adminMemberService.getMembers(pageable);

        // then
        assertThat(result.pageInfo().numberOfElements()).isEqualTo(1);
        assertThat(result.pageInfo().size()).isEqualTo(10);
    }

    @Test
    @DisplayName("멤버를 등록할 수 있다.")
    void 멤버_등록_테스트() {
        //given
        Company companyForRegister = companyRepository.save(CompanyFixture.createCompany());

        AdminMemberRegisterInput request = AdminMemberRegisterInput.builder()
                .companyId(companyForRegister.getId())
                .role(MemberRole.COMPANY_CHEF)
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

        AdminMemberRegisterInput request = AdminMemberRegisterInput.builder()
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

        AdminMemberRegisterInput request = AdminMemberRegisterInput.builder()
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

    @Test
    @DisplayName("멤버를 등록할 때, 허용하지 않는 생성 권한의 경우 예외를 던진다.")
    void 멤버_등록_테스트_허용하지_않는_생성_권한() {
        // given
        Company company = companyRepository.save(CompanyFixture.createCompany());

        AdminMemberRegisterInput request = AdminMemberRegisterInput.builder()
                .companyId(company.getId())
                .name("name")
                .role(MemberRole.MEMBER)
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

        AdminMemberUpdateInput request = AdminMemberUpdateInput.builder()
                .id(member.getId())
                .name("updatedName")
                .email("updated@email.email")
                .phone("01087654321")
                .memo("updatedMemo")
                .build();

        // when
        adminMemberService.updateMember(request);

        // then
        Member updatedMember = memberRepository.findAll().getFirst();

        assertThat(updatedMember.getName()).isEqualTo("updatedName");
        assertThat(updatedMember.getEmail()).isEqualTo("updated@email.email");
        assertThat(updatedMember.getPhoneValue()).isEqualTo("01087654321");
        assertThat(updatedMember.getMemo()).isEqualTo("updatedMemo");
    }

    @Test
    @DisplayName("멤버를 수정할 때, 없는 멤버 ID의 경우 예외를 발생시킨다.")
    void 멤버_수정_테스트_없는_멤버() {
        //given
        long invalidMemberId = 1L;
        AdminMemberUpdateInput request = AdminMemberUpdateInput.builder()
                .id(invalidMemberId)
                .name("name")
                .email("updated@email.email")
                .phone("01012345678")
                .memo("memo")
                .build();

        // when
        Throwable thrown = catchThrowable(() -> adminMemberService.updateMember(request));

        // then
        assertThat(thrown).isInstanceOf(CustomException.class);
        CustomException e = (CustomException) thrown;
        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
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

        AdminMemberUpdateInput request = AdminMemberUpdateInput.builder()
                .id(member.getId())
                .name("name")
                .email(alreadyExistEmail)
                .phone("01012345678")
                .memo("memo")
                .build();

        // when
        Throwable thrown = catchThrowable(() -> adminMemberService.updateMember(request));

        // then
        assertThat(thrown).isInstanceOf(CustomException.class);
        CustomException e = (CustomException) thrown;
        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBER_ALREADY_EXIST_BY_EMAIL);
    }

    @Test
    @DisplayName("멤버 정보를 수정할 때, 최고 관리자 이외의 정보일 경우 예외를 던진다.")
    void 멤버_수정_테스트_최고_관리자_이외의_수정() {
        //given
        Company company = companyRepository.save(CompanyFixture.createCompany());
        Member member = memberRepository.save(
                Member.builder()
                        .company(company)
                        .role(MemberRole.MEMBER)
                        .name("preUpdateName")
                        .email("pre@update.email")
                        .password("password")
                        .phone("01012345678")
                        .memo("preUpdatedMemo")
                        .build()
        );

        AdminMemberUpdateInput request = AdminMemberUpdateInput.builder()
                .id(member.getId())
                .name("updatedName")
                .email("updated@email.email")
                .phone("01087654321")
                .memo("updatedMemo")
                .build();

        // when
        Throwable thrown = catchThrowable(() -> adminMemberService.updateMember(request));

        // then
        assertThat(thrown).isInstanceOf(CustomException.class);
        CustomException e = (CustomException) thrown;
        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBER_ACCESS_DENIED);
    }

    @Test
    @DisplayName("멤버를 삭제할 수 있다.")
    void 멤버_삭제_테스트() {
        // given
        Company company = companyRepository.save(CompanyFixture.createCompany());
        Member member = memberRepository.save(MemberFixture.createMember(company, MemberRole.COMPANY_CHEF));

        // when
        adminMemberService.deleteMember(member.getId());

        // then
        member = memberRepository.findById(member.getId()).get();
        assertThat(member.isActive()).isFalse();
    }

    @Test
    @DisplayName("멤버를 삭제할 때, 없는 멤버 ID의 경우 예외를 발생시킨다.")
    void 멤버_삭제_테스트_없는_멤버() {
        //given
        long invalidMemberId = 1L;

        // when
        Throwable thrown = catchThrowable(() -> adminMemberService.deleteMember(invalidMemberId));

        // then
        assertThat(thrown).isInstanceOf(CustomException.class);
        CustomException e = (CustomException) thrown;
        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("멤버 정보를 삭제할 때, 최고 관리자 이외의 정보일 경우 예외를 던진다.")
    void 멤버_삭제_테스트_최고_관리자_이외의_삭제() {
        //given
        Company company = companyRepository.save(CompanyFixture.createCompany());
        Member member = memberRepository.save(
                Member.builder()
                        .company(company)
                        .role(MemberRole.MEMBER)
                        .name("name")
                        .email("email")
                        .password("password")
                        .phone("01012345678")
                        .memo("memo")
                        .build()
        );

        // when
        Throwable thrown = catchThrowable(() -> adminMemberService.deleteMember(member.getId()));

        // then
        assertThat(thrown).isInstanceOf(CustomException.class);
        CustomException e = (CustomException) thrown;
        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBER_ACCESS_DENIED);
    }
}
