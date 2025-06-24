package org.thisway.security.config.policy.auth;

import static org.thisway.security.config.policy.EndpointRule.permitAll;

import java.util.List;
import org.springframework.http.HttpMethod;
import org.thisway.security.config.policy.AuthorizationRule;

public class AuthAuthorizationPolicy {
    public static List<AuthorizationRule> getRules() {
        return List.of(
                // — 비인증(permit all) —
                permitAll(HttpMethod.POST,
                        List.of("/api/auth/login", "/api/auth/verify-code", "/api/logs/gps", "/api/logs/geofence",
                                "/api/logs/power")),

                permitAll(HttpMethod.PUT,
                        List.of("/api/auth/password")),

                permitAll(HttpMethod.GET,
                        List.of("/api/health")));
    }
}
