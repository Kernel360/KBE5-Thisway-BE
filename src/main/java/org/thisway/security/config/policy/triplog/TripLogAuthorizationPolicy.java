package org.thisway.security.config.policy.triplog;

import org.springframework.http.HttpMethod;
import org.thisway.security.config.policy.AuthorizationRule;

import java.util.List;

import static org.thisway.security.config.policy.EndpointRule.withRoles;

public class TripLogAuthorizationPolicy {
    public static List<AuthorizationRule> getRules() {
        return List.of(
                // — 업체 대시보드 —
                withRoles(
                        HttpMethod.GET,
                        List.of("/api/trip-log/{id}"),
                        "COMPANY_ADMIN", "COMPANY_CHEF"
                )

        );
    }
}
