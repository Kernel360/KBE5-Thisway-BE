package org.thisway.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.thisway.company.support.CompanyFixture.createCompany;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;
import org.thisway.company.entity.Company;
import org.thisway.company.repository.CompanyRepository;
import org.thisway.component.EmailComponent;
import org.thisway.component.RedisComponent;
import org.thisway.member.dto.VerificationPayload;
import org.thisway.member.entity.Member;
import org.thisway.member.repository.MemberRepository;
import org.thisway.member.support.MemberFixture;

@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
@Transactional
public class PasswordServiceTest {

    private final PasswordService passwordService;
    private final CompanyRepository companyRepository;
    private final MemberRepository memberRepository;

    @MockitoSpyBean
    private final RedisComponent redisComponent;
    @MockitoSpyBean
    private final EmailComponent emailComponent;
    private final PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("인증코드 요청 시 유효한 이메일을 입력하면 에러가 발생하지 않는다.")
    void 인증코드_발송_성공() {
        Company company = companyRepository.save(createCompany());
        Member member = memberRepository.save(MemberFixture.createMember(company));

        doNothing().when(emailComponent).sendMail(anyString(), anyString(), anyString(), anyMap());

        assertThatCode(() -> passwordService.sendVerificationCode(member.getEmail()))
                .doesNotThrowAnyException();

        verify(redisComponent).storeToRedis(anyString(), anyString(), anyLong(), any(VerificationPayload.class));
        verify(emailComponent).sendMail(anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    @DisplayName("인증코드 요청 시 존재하지 않는 이메일을 입력하면 not_found 에러가 발생한다.")
    void 존재하지_않는_이메일_인증코드_발송_실패() {
        doNothing().when(emailComponent).sendMail(anyString(), anyString(), anyString(), anyMap());

        CustomException e = assertThrows(CustomException.class, () -> passwordService.sendVerificationCode("invalidEmail@example.com"));
        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("인증코드 요청 시 삭제된 이메일을 입력하면 not_found 에러가 발생한다.")
    void 삭제된_이메일_인증코드_발송_실패() {
        Company company = companyRepository.save(createCompany());
        Member member = memberRepository.save(MemberFixture.createInactiveMember(company));

        doNothing().when(emailComponent).sendMail(anyString(), anyString(), anyString(), anyMap());

        CustomException e = assertThrows(CustomException.class, () -> passwordService.sendVerificationCode(member.getEmail()));
        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("비밀번호 변경 요청 시 올바른 인증코드를 입력하면 비밀번호를 변경한다.")
    void 비밀번호_변경_성공() {
        Company company = companyRepository.save(createCompany());
        Member member = memberRepository.save(MemberFixture.createMember(company));

        VerificationPayload entry = new VerificationPayload("123456", System.currentTimeMillis() + 10000);
        doReturn(entry).when(redisComponent).retrieveFromRedis(anyString(), anyString(), any());

        assertThatCode(() -> passwordService.changePassword(member.getEmail(), "123456", "theNewPassword123!"))
                .doesNotThrowAnyException();
        assertThat(passwordEncoder.matches(
                "theNewPassword123!",
                memberRepository.findByEmailAndActiveTrue(member.getEmail()).get().getPassword())
        ).isTrue();

        verify(redisComponent).retrieveFromRedis(anyString(), anyString(), any());
        verify(redisComponent).delete(anyString(), anyString());
    }

    @Test
    @DisplayName("비밀번호 변경 요청 시 틀린 인증코드를 입력하면 invalid_verification_code 에러가 발생한다.")
    void 틀린_인증코드_비밀번호_변경_실패() {
        Company company = companyRepository.save(createCompany());
        Member member = memberRepository.save(MemberFixture.createMember(company));

        VerificationPayload entry = new VerificationPayload("123456", System.currentTimeMillis() + 10000);
        doReturn(entry).when(redisComponent).retrieveFromRedis(anyString(), anyString(), any());

        CustomException e = assertThrows(CustomException.class, () ->
                passwordService.changePassword(member.getEmail(), "654321", "theNewPassword123!"));
        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_INVALID_VERIFICATION_CODE);
    }

    @Test
    @DisplayName("비밀번호 변경 요청 시 만료된 인증코드를 입력하면 invalid_verification_code 에러가 발생한다.")
    void 만료된_인증코드_비밀번호_변경_실패() {
        Company company = companyRepository.save(createCompany());
        Member member = memberRepository.save(MemberFixture.createMember(company));

        VerificationPayload entry = new VerificationPayload("123456", System.currentTimeMillis() - 10000);
        doReturn(entry).when(redisComponent).retrieveFromRedis(anyString(), anyString(), any());

        CustomException e = assertThrows(CustomException.class, () ->
                passwordService.changePassword(member.getEmail(), "123456", "theNewPassword123!"));
        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_INVALID_VERIFICATION_CODE);
    }

    @Test
    @DisplayName("비밀번호 형식이 알파벳, 숫자, 특수문자를 포함하여 8-20자에 해당하지 않으면 member_invalid_password 에러가 발생한다.")
    void 비밀번호_형식_오류시_비밀번호_변경_실패() {
        Company company = companyRepository.save(createCompany());
        Member member = memberRepository.save(MemberFixture.createMember(company));

        VerificationPayload entry = new VerificationPayload("123456", System.currentTimeMillis() + 10000);
        doReturn(entry).when(redisComponent).retrieveFromRedis(anyString(), anyString(), any());

        CustomException e = assertThrows(CustomException.class, () ->
                passwordService.changePassword(member.getEmail(), "123456", "theNewPassword"));
        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBER_INVALID_PASSWORD);
    }

}
