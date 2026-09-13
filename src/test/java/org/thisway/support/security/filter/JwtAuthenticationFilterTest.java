package org.thisway.support.security.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.thisway.member.domain.MemberReader;
import org.thisway.member.domain.MemberRole;
import org.thisway.support.security.dto.request.MemberDetails;
import org.thisway.support.security.utils.JwtTokenProvider;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;

class JwtAuthenticationFilterTest {
    private MockMvc mockMvc;
    private JwtTokenProvider jwtTokenProvider;
    private MemberReader memberReader;
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = Mockito.mock(JwtTokenProvider.class);
        memberReader = Mockito.mock(MemberReader.class);

        // 더미 컨트롤러: 필터 통과 시 200 OK
        @RestController
        class DummyController {
            // Dummy controller for testing
            @GetMapping("/dummy")
            public void ok() {
            }
        }

        mockMvc = MockMvcBuilders
                .standaloneSetup(new DummyController())
                .addFilter(new JwtAuthenticationFilter(jwtTokenProvider, memberReader))
                .build();

        jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtTokenProvider, memberReader);

        // 매 테스트 전 컨텍스트 초기화
        SecurityContextHolder.clearContext();
    }

    @Test
    void 토큰_헤더가_없으면_컨트롤러가_호출되어_200_OK() throws Exception {
        mockMvc.perform(get("/dummy"))
                .andExpect(result -> {
                    assertThat(result
                            .getResponse()
                            .getStatus())
                            .isEqualTo(200);
                    // SecurityContext는 아무 것도 세팅되지 않아야 함
                    assertThat(SecurityContextHolder
                            .getContext()
                            .getAuthentication())
                            .isNull();
                });
    }

    @Test
    void 유효한_토큰이면_SecurityContext에_Authentication이_세팅되고_컨트롤러_실행() throws Exception {
        // This unit suite verifies filter orchestration with mocked signature and
        // identity results; the current-account DB predicate has integration tests.
        validSignedClaims();
        given(memberReader.isCurrentIdentity(1L, "alice", 1L, MemberRole.MEMBER)).willReturn(true);

        // when & then
        mockMvc.perform(get("/dummy")
                        .header("Authorization",
                                "Bearer valid-token"))
                .andExpect(result -> {
                    assertThat(result
                            .getResponse()
                            .getStatus()).isEqualTo(200);

                    Authentication auth = SecurityContextHolder
                            .getContext()
                            .getAuthentication();

                    assertThat(auth).isNotNull();
                    assertThat(auth.getName()).isEqualTo("alice");
                    assertThat(((MemberDetails) auth.getPrincipal()).getMemberId()).isEqualTo(1L);
                    assertThat(auth.getAuthorities())
                            .extracting("authority")
                            .containsExactly("ROLE_MEMBER");
                });
    }

    @Test
    void 잘못된_JWT로_dummy_요청시_BadCredentialsException_던진다() throws ServletException, IOException {
        // given : JwtTokenProvider.validateTokenAndGetClaims()가 예외 던지도록 스텁
        String badToken = "invalid.token.value";
        given(jwtTokenProvider.validateTokenAndGetClaims(badToken))
                .willThrow(new BadCredentialsException("Invalid JWT token"));

        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        req.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + badToken);

        // dummy 체인 : 호출되면 바로 실패
        FilterChain chain = (r, s) -> {
            throw new AssertionError("should not reach");
        };

        // when & then
        assertThatThrownBy(() -> jwtAuthenticationFilter.doFilterInternal(req, res, chain))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid JWT");
        Mockito.verifyNoInteractions(memberReader);
    }

    @Test
    void 서명이_유효해도_현재_DB주체가_아니면_인증과_후속체인을_허용하지_않는다() {
        validSignedClaims();
        given(memberReader.isCurrentIdentity(1L, "alice", 1L, MemberRole.MEMBER)).willReturn(false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-token");
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        assertThatThrownBy(() -> jwtAuthenticationFilter.doFilterInternal(request, new MockHttpServletResponse(),
                (req, res) -> chainCalled.set(true)))
                .isInstanceOf(BadCredentialsException.class);
        assertThat(chainCalled.get()).isFalse();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void 현재주체_DB조회_장애는_인증실패로_바꾸지_않고_후속체인을_막는다() {
        validSignedClaims();
        var failure = new DataAccessResourceFailureException("fixture database unavailable");
        given(memberReader.isCurrentIdentity(1L, "alice", 1L, MemberRole.MEMBER)).willThrow(failure);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-token");
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        assertThatThrownBy(() -> jwtAuthenticationFilter.doFilterInternal(request, new MockHttpServletResponse(),
                (req, res) -> chainCalled.set(true)))
                .isSameAs(failure);
        assertThat(chainCalled.get()).isFalse();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void 토큰_헤더가_없으면_filterChain이_정상_호출된다() throws ServletException, IOException {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainCalled.set(true);

        // when
        jwtAuthenticationFilter.doFilterInternal(request, response, chain);

        // then
        assertThat(chainCalled.get()).isTrue(); // 다음 체인으로 정상 진행
        assertThat(response.getStatus()).isEqualTo(200); // 상태 코드는 기본값(200) 유지
        assertThat(SecurityContextHolder.getContext().getAuthentication()) // 인증 정보는 세팅되지 않음
                .isNull();
        Mockito.verifyNoInteractions(memberReader);
    }

    private void validSignedClaims() {
        Claims claims = Mockito.mock(Claims.class);
        given(claims.getSubject()).willReturn("alice");
        given(claims.get("roles", List.class)).willReturn(List.of("MEMBER"));
        given(claims.get("companyId", Long.class)).willReturn(1L);
        given(claims.get("memberId", Long.class)).willReturn(1L);
        given(jwtTokenProvider.validateTokenAndGetClaims("valid-token")).willReturn(claims);
    }
}
