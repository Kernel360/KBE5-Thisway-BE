package org.thisway.support.security.config.policy;

import java.util.List;

import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

/**
 * 엔드포인트별 HTTP 메서드, 경로 패턴, 권한(역할)을 기반으로
 * Spring Security의 접근 제어 정책을 정의하는 레코드입니다.
 * <p>
 * {@link AuthorizationRule}을 구현하여, 지정된 조건에 따라
 * 접근 허용 또는 권한 검증을 수행합니다.
 *
 * @param method   적용할 HTTP 메서드
 * @param patterns 적용할 경로 패턴 목록
 * @param roles    허용할 권한(역할) 목록 (비어 있으면 permitAll)
 */
public record EndpointRule(
        HttpMethod method,
        List<String> patterns,
        String... roles
) implements AuthorizationRule {

    @Override
    public void configure(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>
                    .AuthorizationManagerRequestMatcherRegistry registry
    ) {
        AuthorizeHttpRequestsConfigurer<HttpSecurity>
                .AuthorizedUrl spec = method != null
                ? registry.requestMatchers(method, patterns.toArray(String[]::new))
                : registry.requestMatchers(patterns.toArray(String[]::new));

        if (roles.length == 0)
            spec.permitAll();
        else
            spec.hasAnyRole(roles);
    }

    /**
     * 주어진 HTTP 메서드와 경로 패턴에 대해 모든 요청을 허용하는 EndpointRule을 생성합니다.
     *
     * @param method   허용할 HTTP 메서드 (예: GET, POST 등)
     * @param patterns 허용할 경로 패턴 목록
     * @return 모든 요청을 허용하는 EndpointRule 인스턴스
     */
    public static EndpointRule permitAll(
            HttpMethod method,
            List<String> patterns
    ) {
        return new EndpointRule(method, patterns);
    }

    /**
     * 주어진 경로 패턴에 대해 모든 요청을 허용하는 EndpointRule을 생성합니다.
     *
     * @param patterns 허용할 경로 패턴 목록
     * @return 모든 요청을 허용하는 EndpointRule 인스턴스
     */
    public static EndpointRule permitAll(
            List<String> patterns
    ) {
        return new EndpointRule(null, patterns);
    }

    /**
     * 주어진 HTTP 메서드, 경로 패턴, 권한(역할)에 대해 접근을 허용하는 EndpointRule을 생성합니다.
     *
     * @param method   허용할 HTTP 메서드 (예: GET, POST 등)
     * @param patterns 허용할 경로 패턴 목록
     * @param roles    허용할 권한(역할) 목록
     * @return 지정된 조건에 맞는 EndpointRule 인스턴스
     */
    public static EndpointRule withRoles(
            HttpMethod method,
            List<String> patterns,
            String... roles
    ) {
        return new EndpointRule(method, patterns, roles);
    }

    /**
     * 주어진 경로 패턴과 권한(역할)에 대해 접근을 허용하는 EndpointRule을 생성합니다.
     *
     * @param patterns 허용할 경로 패턴 목록
     * @param roles    허용할 권한(역할) 목록
     * @return 지정된 조건에 맞는 EndpointRule 인스턴스
     */
    public static EndpointRule withRoles(
            List<String> patterns,
            String... roles
    ) {
        return new EndpointRule(null, patterns, roles);
    }
}
