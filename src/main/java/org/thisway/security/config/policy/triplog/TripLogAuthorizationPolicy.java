package org.thisway.security.config.policy.triplog;

import org.springframework.http.HttpMethod;
import org.thisway.security.config.policy.AuthorizationRule;

import java.util.List;

import static org.thisway.security.config.policy.EndpointRule.withRoles;

public class TripLogAuthorizationPolicy {
    public static List<AuthorizationRule> getRules() {
        return List.of(
                // 실시간 운행 기록
                withRoles(
                        HttpMethod.GET,
                        List.of("/api/trip-log/current/{id}"),
                        "COMPANY_ADMIN", "COMPANY_CHEF", "MEMBER"
                ),

                // 차량 상세보기 페이지
                withRoles(
                        HttpMethod.GET,
                        List.of("/api/trip-log/**"),
                        "COMPANY_ADMIN", "COMPANY_CHEF"
                )

        );
    }
}
