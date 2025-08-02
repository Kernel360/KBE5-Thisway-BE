package org.thisway.auth.domain;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thisway.auth.application.AuthCommand;
import org.thisway.auth.application.AuthInfo;
import org.thisway.auth.application.AuthServiceImpl;
import org.thisway.company.domain.Company;
import org.thisway.member.domain.Member;
import org.thisway.member.domain.MemberReader;
import org.thisway.member.domain.MemberRole;
import org.thisway.support.common.CustomException;
import org.thisway.support.common.ErrorCode;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private MemberReader memberReader;

    @Mock
    private PasswordEncoderMatch passwordEncoder;

    @Mock
    private TokenGenerator tokenGenerator;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    @DisplayName("로그인 성공 시 토큰 반환")
    void 로그인_성공() {
        // given
        String email = "test@example.com";
        String rawPassword = "password";
        String encodedPassword = "encodedPassword";
        var request = AuthCommand.LoginRequest.builder()
                .email(email)
                .password(rawPassword)
                .build();

        Company company = Company.builder()
                .name("name")
                .crn("crn")
                .contact("contact")
                .addrRoad("road")
                .addrDetail("detail")
                .memo("memo")
                .gpsCycle(30)
                .build();
        Member member = Member.builder()
                .company(company)
                .role(MemberRole.MEMBER)
                .name("username")
                .email(email)
                .password(encodedPassword)
                .phone("01000000000")
                .memo("memo")
                .build();

        AuthToken expectedToken = new AuthToken("access", "refresh");

        when(memberReader.requireActiveMemberByEmail(email)).thenReturn(member);
        when(tokenGenerator.generator(member)).thenReturn(expectedToken);
        doNothing().when(passwordEncoder)
                .assertMatches(rawPassword, encodedPassword);

        // when
        AuthInfo.LoginResult actualResponse = authService.login(request);

        // then
        assertAll(
                () -> assertEquals(
                        expectedToken.getAccessToken(),
                        actualResponse.getAccessToken()),
                () -> assertEquals(
                        expectedToken.getRefreshToken(),
                        actualResponse.getRefreshToken()));
        verify(memberReader).requireActiveMemberByEmail(email);
        verify(passwordEncoder).assertMatches(rawPassword, encodedPassword);
        verify(tokenGenerator).generator(member);
    }

    @Test
    @DisplayName("회원이 존재하지 않을 때 예외 발생")
    void 로그인_회원_없음_예외() {
        // given
        String email = "test@example.com";
        String password = "password";
        var request = AuthCommand.LoginRequest.builder()
                .email(email)
                .password(password)
                .build();

        when(memberReader.requireActiveMemberByEmail(email))
                .thenThrow(new CustomException(ErrorCode.AUTH_MEMBER_NOT_FOUND));

        // when & then
        CustomException exception = assertThrows(CustomException.class,
                () -> authService.login(request));
        assertEquals(ErrorCode.AUTH_MEMBER_NOT_FOUND, exception.getErrorCode());
        verify(memberReader).requireActiveMemberByEmail(email);
        verifyNoInteractions(passwordEncoder, tokenGenerator);
    }

    @Test
    @DisplayName("비밀번호 불일치 시 예외 발생")
    void 로그인_비밀번호_불일치_예외() {
        // given
        String email = "test@example.com";
        String rawPassword = "password";
        String encodedPassword = "encodedPassword";
        var request = AuthCommand.LoginRequest.builder()
                .email(email)
                .password(rawPassword)
                .build();

        Company company = Company.builder()
                .name("name")
                .crn("crn")
                .contact("contact")
                .addrRoad("road")
                .addrDetail("detail")
                .memo("memo")
                .gpsCycle(30)
                .build();
        Member member = Member.builder()
                .company(company)
                .role(MemberRole.MEMBER)
                .name("username")
                .email(email)
                .password(encodedPassword)
                .phone("01000000000")
                .memo("memo")
                .build();

        when(memberReader.requireActiveMemberByEmail(email)).thenReturn(member);
        doThrow(new CustomException(ErrorCode.AUTH_PASSWORD_NOT_MATCH))
                .when(passwordEncoder)
                .assertMatches(rawPassword, encodedPassword);

        // when & then
        CustomException exception = assertThrows(CustomException.class,
                () -> authService.login(request));
        assertEquals(ErrorCode.AUTH_PASSWORD_NOT_MATCH, exception.getErrorCode());
    }
}
