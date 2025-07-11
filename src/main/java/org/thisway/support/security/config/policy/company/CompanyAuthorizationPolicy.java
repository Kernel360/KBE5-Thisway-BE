package org.thisway.support.security.config.policy.company;

import static org.thisway.support.security.config.policy.EndpointRule.withRoles;

import java.util.List;

import org.springframework.http.HttpMethod;
import org.thisway.support.security.config.policy.AuthorizationRule;

public class CompanyAuthorizationPolicy {
    public static List<AuthorizationRule> getRules() {
        return List.of(
                // — companies CRUD —
                withRoles(
                        HttpMethod.GET,
                        List.of("/api/admin/companies/{id}"),
                        "ADMIN"
                ),
                withRoles(
                        HttpMethod.GET,
                        List.of("/api/admin/companies"),
                        "ADMIN"
                ),
                withRoles(
                        HttpMethod.POST,
                        List.of("/api/admin/companies"),
                        "ADMIN"
                ),
                withRoles(
                        HttpMethod.PUT,
                        List.of("/api/admin/companies/{id}"),
                        "ADMIN"
                ),
                withRoles(
                        HttpMethod.DELETE,
                        List.of("/api/admin/companies/{id}"),
                        "ADMIN"
                )
        );
    }

}
