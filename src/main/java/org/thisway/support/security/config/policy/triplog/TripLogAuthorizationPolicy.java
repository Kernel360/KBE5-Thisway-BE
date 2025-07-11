package org.thisway.support.security.config.policy.triplog;

import org.springframework.http.HttpMethod;
import org.thisway.support.security.config.policy.AuthorizationRule;

import java.util.List;

import static org.thisway.support.security.config.policy.EndpointRule.permitAll;
import static org.thisway.support.security.config.policy.EndpointRule.withRoles;

public class TripLogAuthorizationPolicy {
    public static List<AuthorizationRule> getRules() {
        return List.of(
                permitAll(HttpMethod.GET,
                        List.of("/api/trip-log/detail/stream/{id}", "/api/trip-log/current/stream/{id}")
                ),

                // 실시간 운행 기록
                withRoles(
                        HttpMethod.GET,
                        List.of("/api/trip-log/current/{id}", "/api/trip-log/{id}", "/api/trip-log", "/api/trip-log/detail/{id}"),
                        "COMPANY_ADMIN", "COMPANY_CHEF", "MEMBER"
                )
        );
    }
}
