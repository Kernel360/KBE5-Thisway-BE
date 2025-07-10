package org.thisway.support.security.config.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EndpointRuleTest {

    @Test
    @DisplayName("permitAll로 생성 시 roles가 isEmpty이고, method/patterns가 올바르게 설정된다")
    void 모든요청허용_생성_검증() {
        // given
        HttpMethod method = HttpMethod.GET;
        List<String> patterns = List.of("/api/**");

        // when
        EndpointRule rule = EndpointRule.permitAll(method, patterns);

        // then
        assertThat(rule.method()).isEqualTo(method);
        assertThat(rule.patterns()).containsExactlyElementsOf(patterns);
        assertThat(rule.roles()).isEmpty();
    }

    @Test
    @DisplayName("withRoles로 생성 시 roles가 올바르게 설정된다")
    void 권한지정_생성_검증() {
        // given
        HttpMethod method = HttpMethod.POST;
        List<String> patterns = List.of("/admin/**");
        List<String> roles = List.of("ADMIN", "MANAGER");

        // when
        EndpointRule rule = EndpointRule.withRoles(
                method,
                patterns,
                roles.toArray(new String[0]));

        // then
        assertThat(rule.method()).isEqualTo(method);
        assertThat(rule.patterns()).containsExactlyElementsOf(patterns);
        assertThat(rule.roles()).containsExactlyElementsOf(roles);
    }
}
