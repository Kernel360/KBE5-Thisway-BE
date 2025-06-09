package org.thisway.security.config.policy;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

/**
 * HTTP 요청에 대한 인가(Authorization) 규칙을 정의하는 인터페이스입니다.
 * 구현체에서 구체적인 인가 규칙을 설정할 수 있습니다.
 */
public interface AuthorizationRule {
    /**
     * 인가 규칙을 설정합니다.
     *
     * @param registry 인가 규칙을 등록할 레지스트리 객체
     */
    void configure(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>
                .AuthorizationManagerRequestMatcherRegistry registry
    );
}

