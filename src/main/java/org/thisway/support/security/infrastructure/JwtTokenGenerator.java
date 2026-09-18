package org.thisway.support.security.infrastructure;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.thisway.auth.domain.AuthToken;
import org.thisway.auth.domain.TokenGenerator;
import org.thisway.company.domain.Company;
import org.thisway.member.domain.Member;
import org.thisway.support.security.utils.JwtTokenProvider;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtTokenGenerator implements TokenGenerator {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public AuthToken generator(Member member) {

        // 1. Claims 생성 과정
        Map<String, Object> claims = new HashMap<>();

        Company company = member.getCompany();
        if (company != null)
            claims.put("companyId", company.getId());

        claims.put("roles", List.of(member.getRole()));

        // 2. 토큰 생성 과정
        // TODO: RefreshToken은 추후 구현
        String subject = member.getEmail();
        String accessToken = jwtTokenProvider.generateAccessToken(subject, claims);
        String refreshToken = jwtTokenProvider.generateAccessToken(subject, claims);

        return AuthToken.of(accessToken, refreshToken);
    }
}
