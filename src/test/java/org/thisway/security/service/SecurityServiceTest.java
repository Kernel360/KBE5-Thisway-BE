package org.thisway.security.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;
import org.thisway.member.entity.Member;
import org.thisway.member.repository.MemberRepository;

class SecurityServiceTest {

    private MemberRepository memberRepository;
    private SecurityService securityService;

    @BeforeEach
    void setUp() {
        memberRepository = mock(MemberRepository.class);
        securityService = new SecurityService(memberRepository);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("인증된 사용자의 Member 정보를 성공적으로 조회할 수 있다.")
    void 현재_회원_조회_성공() {
        // given
        String email = "email@example.com";
        UserDetails user = new User(email, "password", new ArrayList<>());
        Member member = mock(Member.class);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities())
        );

        when(memberRepository.findByEmailAndActiveTrue(email))
                .thenReturn(Optional.of(member));

        // when
        Member result = securityService.getCurrentMember();

        // then
        assertThat(result).isEqualTo(member);
        verify(memberRepository).findByEmailAndActiveTrue(email);
    }

    @Test
    @DisplayName("인증되지 않은 상태에서 현재 회원 정보를 조회하면 예외가 발생한다.")
    void 현재_회원_조회_실패_인증안됨() {
        // given
        SecurityContextHolder.clearContext(); // 인증정보 없음

        // when & then
        CustomException exception = assertThrows(CustomException.class, () ->
                securityService.getCurrentMember()
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_UNAUTHENTICATED);
    }

    @Test
    @DisplayName("인증정보는 있으나 해당 사용자가 존재하지 않을 경우 예외가 발생한다.")
    void 현재_회원_조회_실패_DB에_없음() {
        // given
        String email = "email@example.com";
        UserDetails user = new User(email, "password", new ArrayList<>());

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities())
        );

        when(memberRepository.findByEmailAndActiveTrue(email)).thenReturn(Optional.empty());

        // when & then
        CustomException exception = assertThrows(CustomException.class, () ->
                securityService.getCurrentMember()
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_INVALID_AUTHENTICATION);
    }
}
