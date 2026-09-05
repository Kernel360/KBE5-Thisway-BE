package org.thisway.support.security.config.policy.statistics;

import org.springframework.http.HttpMethod;
import org.thisway.support.security.config.policy.AuthorizationRule;

import java.util.List;

import static org.thisway.support.security.config.policy.EndpointRule.withRoles;

public class StatisticsAuthorizationPolicy {
    public static List<AuthorizationRule> getRules() {
        return List.of(
                // 통계 조회 API
                withRoles(
                        HttpMethod.GET,
                        List.of("/api/statistics/**"),
                        "COMPANY_ADMIN", "COMPANY_CHEF"
                ),

                // 임의 companyId를 받는 수동 통계 저장 API
                withRoles(
                        HttpMethod.POST,
                        List.of("/api/statistics/save"),
                        "ADMIN"
                )

        );
    }
}
