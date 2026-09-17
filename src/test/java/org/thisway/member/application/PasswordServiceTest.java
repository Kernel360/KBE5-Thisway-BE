package org.thisway.member.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.thisway.company.domain.Company;
import org.thisway.member.domain.Member;
import org.thisway.member.infrastructure.MemberRepository;
import org.thisway.support.common.CustomException;
import org.thisway.support.common.ErrorCode;
import org.thisway.support.component.EmailComponent;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Orchestration contracts only; Redis/MySQL atomicity is covered by PasswordResetIntegrationTest. */
class PasswordServiceTest {
    private static final String EMAIL = "Account@example.test";
    private static final String PASSWORD = "NewPassword123!";
    private MemberRepository members;
    private EmailComponent email;
    private PasswordResetChallengeStore challenges;
    private PasswordEncoder encoder;
    private PasswordResetPasswordUpdater updater;
    private PasswordService service;

    @BeforeEach
    void fixture() {
        members = mock(MemberRepository.class);
        email = mock(EmailComponent.class);
        challenges = mock(PasswordResetChallengeStore.class);
        encoder = mock(PasswordEncoder.class);
        updater = mock(PasswordResetPasswordUpdater.class);
        service = new PasswordService(members, email, challenges, encoder, updater);
        Member member = mock(Member.class);
        Company company = mock(Company.class);
        when(member.getId()).thenReturn(7L);
        when(member.getEmail()).thenReturn(EMAIL);
        when(member.getCompany()).thenReturn(company);
        when(company.getId()).thenReturn(11L);
        when(members.findActiveLoginMemberByEmail(anyString())).thenReturn(Optional.of(member));
        when(challenges.issue(eq(7L), eq(EMAIL), anyString(), anyString()))
                .thenReturn(PasswordResetChallengeStore.IssueResult.ISSUED);
        when(challenges.consume(7L, EMAIL, "123456"))
                .thenReturn(PasswordResetChallengeStore.ConsumeResult.CONSUMED);
        when(encoder.encode(PASSWORD)).thenReturn("encoded-fixture");
    }

    @Test
    void 발송은_trim한_이메일을_DB로_해석하고_PK_한도와_DB_주소를_사용한다() {
        service.sendVerificationCode("  ACCOUNT@example.test  ");
        verify(members).findActiveLoginMemberByEmail("ACCOUNT@example.test");
        var issuance = org.mockito.ArgumentCaptor.forClass(String.class);
        var code = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(challenges).issue(eq(7L), eq(EMAIL), issuance.capture(), code.capture());
        assertThat(issuance.getValue()).matches("[0-9a-f-]{36}");
        assertThat(code.getValue()).matches("[0-9]{6}");
        verify(email).sendMail(EMAIL, "ThisWay 이메일 인증 코드", "email-content", Map.of("code", code.getValue()));
    }

    @Test
    void 발송_한도에_걸리면_429이며_메일을_보내지_않는다() {
        when(challenges.issue(eq(7L), eq(EMAIL), anyString(), anyString()))
                .thenReturn(PasswordResetChallengeStore.IssueResult.RATE_LIMITED);
        assertError(() -> service.sendVerificationCode(EMAIL), ErrorCode.AUTH_VERIFICATION_RATE_LIMITED);
        verifyNoInteractions(email);
    }

    @Test
    void 발송_승인이_불확실하면_메일을_보내지_않는다() {
        when(challenges.issue(eq(7L), eq(EMAIL), anyString(), anyString())).thenReturn(null);
        assertThatThrownBy(() -> service.sendVerificationCode(EMAIL)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(email);
    }

    @Test
    void SMTP_실패는_자신의_issuance만_조건부취소한다() {
        doThrow(new CustomException(ErrorCode.EMAIL_SEND_ERROR)).when(email)
                .sendMail(anyString(), anyString(), anyString(), anyMap());
        assertError(() -> service.sendVerificationCode(EMAIL), ErrorCode.EMAIL_SEND_ERROR);
        var issuance = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(challenges).issue(eq(7L), eq(EMAIL), issuance.capture(), anyString());
        verify(challenges).cancel(7L, issuance.getValue());
    }

    @Test
    void SMTP_후_취소마저_실패해도_원래_실패를_반환하고_재발급하지_않는다() {
        doThrow(new CustomException(ErrorCode.EMAIL_SEND_ERROR)).when(email)
                .sendMail(anyString(), anyString(), anyString(), anyMap());
        doThrow(new DataAccessResourceFailureException("fixture")).when(challenges).cancel(anyLong(), anyString());
        assertError(() -> service.sendVerificationCode(EMAIL), ErrorCode.EMAIL_SEND_ERROR);
        verify(challenges, times(1)).issue(eq(7L), eq(EMAIL), anyString(), anyString());
    }

    @Test
    void 없는_회원이나_비활성_회사에_코드를_발송하지_않는다() {
        when(members.findActiveLoginMemberByEmail(EMAIL)).thenReturn(Optional.empty());
        assertError(() -> service.sendVerificationCode(EMAIL), ErrorCode.MEMBER_NOT_FOUND);
        verifyNoInteractions(challenges, email);
    }

    @Test
    void null_이메일은_계정조회전에_거부한다() {
        assertError(() -> service.sendVerificationCode(null), ErrorCode.MEMBER_INVALID_EMAIL);
        verifyNoInteractions(members, challenges, email);
    }

    @Test
    void 비밀번호형식은_코드소비전에_검증한다() {
        assertError(() -> service.changePassword(EMAIL, "123456", "invalid"), ErrorCode.MEMBER_INVALID_PASSWORD);
        verifyNoInteractions(members, challenges, encoder, updater);
    }

    @Test
    void null과_비정상코드는_계정조회전에_거부한다() {
        for (String code : new String[]{null, "", "12345", "1234567", "12345a"}) {
            assertError(() -> service.changePassword(EMAIL, code, PASSWORD), ErrorCode.AUTH_INVALID_VERIFICATION_CODE);
        }
        verifyNoInteractions(members, challenges, encoder, updater);
    }

    @Test
    void 틀린코드나_시도한도초과는_비밀번호를_변경하지_않는다() {
        when(challenges.consume(7L, EMAIL, "123456"))
                .thenReturn(PasswordResetChallengeStore.ConsumeResult.INVALID)
                .thenReturn(PasswordResetChallengeStore.ConsumeResult.RATE_LIMITED);
        assertError(() -> service.changePassword(EMAIL, "123456", PASSWORD), ErrorCode.AUTH_INVALID_VERIFICATION_CODE);
        assertError(() -> service.changePassword(EMAIL, "123456", PASSWORD), ErrorCode.AUTH_VERIFICATION_RATE_LIMITED);
        verifyNoInteractions(encoder, updater);
    }

    @Test
    void 성공한_소비이후_해시를_만들고_동일계정_스냅샷으로_DB쓰기한다() {
        service.changePassword(EMAIL, "123456", PASSWORD);
        var order = inOrder(challenges, encoder, updater);
        order.verify(challenges).consume(7L, EMAIL, "123456");
        order.verify(encoder).encode(PASSWORD);
        order.verify(updater).update(7L, EMAIL, 11L, "encoded-fixture");
        verify(challenges, never()).cancel(anyLong(), anyString());
    }

    @Test
    void DB_실패후_소비한_코드는_복원하거나_자동재발급하지_않는다() {
        doThrow(new DataAccessResourceFailureException("fixture")).when(updater)
                .update(7L, EMAIL, 11L, "encoded-fixture");
        assertThatThrownBy(() -> service.changePassword(EMAIL, "123456", PASSWORD))
                .isInstanceOf(DataAccessResourceFailureException.class);
        verify(challenges).consume(7L, EMAIL, "123456");
        verify(challenges, never()).issue(anyLong(), anyString(), anyString(), anyString());
        verify(challenges, never()).cancel(anyLong(), anyString());
    }

    private void assertError(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(CustomException.class,
                failure -> assertThat(failure.getErrorCode()).isEqualTo(expected));
    }
}
