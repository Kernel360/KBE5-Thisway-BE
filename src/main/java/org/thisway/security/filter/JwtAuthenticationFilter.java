package org.thisway.security.filter;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.thisway.member.entity.MemberRole;
import org.thisway.security.dto.request.MemberDetails;
import org.thisway.security.utils.JwtTokenUtil;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenUtil jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = extractToken(request);

        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // 서명, 만료 검증 & Claims 파싱
        Claims claims = jwtTokenProvider.validateTokenAndGetClaims(token);

        // sub(Claims) 존재 여부 검사
        String username = claims.getSubject();

        if (username == null || username.isBlank()) {
            throw new BadCredentialsException("Invalid JWT token: missing subject");
        }

        @SuppressWarnings("unchecked")
        List<String> roles = claims.get("roles", List.class);
        if (roles == null)
            roles = List.of();

        Long companyId = claims.get("companyId", Long.class);
        if (companyId == null)
            throw new BadCredentialsException("Invalid JWT token: missing companyId");

        MemberRole role = MemberRole.valueOf(roles.getFirst());

        MemberDetails memberDetails = MemberDetails.builder()
                .username(username)
                .companyId(companyId)
                .role(role)
                .build();

        String[] authorities = roles.stream()
                .map(r -> r.startsWith("ROLE_") ? r : "ROLE_" + r)
                .toArray(String[]::new);
        Authentication auth = new UsernamePasswordAuthenticationToken(
                memberDetails,
                null,
                AuthorityUtils.createAuthorityList(authorities));

        SecurityContextHolder
                .getContext()
                .setAuthentication(auth);

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith("Bearer ")) {
            return header.substring("Bearer ".length());
        }
        return null;
    }

    // 이렇게 사용할지 아직 미정
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 로그인이나 회원가입 같이 JWT 검증이 필요 없는 요청은 필터를 건너뛴다.
        String path = request.getServletPath();
        return "/api/auth/login".equals(path);
    }
}
