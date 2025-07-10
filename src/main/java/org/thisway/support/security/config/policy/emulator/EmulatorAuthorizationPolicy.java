package org.thisway.support.security.config.policy.emulator;

import static org.thisway.support.security.config.policy.EndpointRule.withRoles;

import java.util.List;

import org.springframework.http.HttpMethod;
import org.thisway.support.security.config.policy.AuthorizationRule;

public class EmulatorAuthorizationPolicy {

    public static List<AuthorizationRule> getRules() {
        return List.of(
                // — emulators CRUD —
                withRoles(
                        HttpMethod.POST,
                        List.of("/api/emulators"),
                        "COMPANY_ADMIN", "COMPANY_CHEF"
                ),
                withRoles(
                        HttpMethod.DELETE,
                        List.of("/api/emulators/{id}"),
                        "COMPANY_ADMIN", "COMPANY_CHEF"
                ),
                withRoles(
                        HttpMethod.PATCH,
                        List.of("/api/emulators/{id}"),
                        "COMPANY_ADMIN", "COMPANY_CHEF"
                ),

                withRoles(
                        HttpMethod.GET,
                        List.of("/api/emulators/{id}", "/api/emulators"),
                        "COMPANY_ADMIN", "COMPANY_CHEF"
                ));
    }
}
