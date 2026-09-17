package org.thisway.support.security.filter;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.thisway.member.domain.MemberRole;
import org.thisway.member.domain.MemberReader;
import org.thisway.support.security.dto.request.MemberDetails;
import org.thisway.support.security.utils.JwtTokenProvider;

import java.io.IOException;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final MemberReader memberReader;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return "/api/auth/login".equals(path);
    }

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

        // Tokens issued by this application contain exactly one domain role.
        // Validate the structure before constructing either principal or authorities.
        List<?> roles = claims.get("roles", List.class);
        if (roles == null || roles.size() != 1 || !(roles.getFirst() instanceof String roleName)) {
            throw new BadCredentialsException("Invalid JWT token: invalid roles");
        }
        MemberRole role;
        try {
            role = MemberRole.valueOf(roleName);
        } catch (IllegalArgumentException invalidRole) {
            throw new BadCredentialsException("Invalid JWT token: invalid roles");
        }

        Long companyId = claims.get("companyId", Long.class);
        if (companyId == null || companyId <= 0)
            throw new BadCredentialsException("Invalid JWT token: invalid companyId");

        Long memberId = claims.get("memberId", Long.class);
        if (memberId == null || memberId <= 0)
            throw new BadCredentialsException("Invalid JWT token: invalid memberId");

        // A valid signature does not establish current membership or company access.
        // Include the immutable member ID so a reused email cannot rebind an old token.
        if (!memberReader.isCurrentIdentity(memberId, username, companyId, role))
            throw new BadCredentialsException("Invalid JWT token: inactive or changed identity");

        MemberDetails memberDetails = MemberDetails.builder()
                .memberId(memberId)
                .username(username)
                .companyId(companyId)
                .role(role)
                .build();

        Authentication auth = new UsernamePasswordAuthenticationToken(
                memberDetails,
                null,
                AuthorityUtils.createAuthorityList("ROLE_" + role.name()));

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

}
