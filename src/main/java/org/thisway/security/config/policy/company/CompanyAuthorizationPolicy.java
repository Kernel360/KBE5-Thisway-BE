package org.thisway.security.config.policy.company;

import java.util.List;

import org.springframework.http.HttpMethod;
import org.thisway.security.config.policy.AuthorizationRule;

import static org.thisway.security.config.policy.EndpointRule.withRoles;

public class CompanyAuthorizationPolicy {
    public static List<AuthorizationRule> getRules() {
        return List.of(
                // — 업체 대시보드 —
                withRoles(
                        HttpMethod.GET,
                        List.of("/api/companies/dashboard"),
                        "COMPANY_ADMIN", "COMPANY_CHEF"),

                // — companies CRUD —
                withRoles(
                        HttpMethod.POST,
                        List.of("/api/companies"),
                        "ADMIN"),
                withRoles(
                        HttpMethod.DELETE,
                        List.of("/api/companies/{id}"),
                        "ADMIN"),
                withRoles(
                        HttpMethod.GET,
                        List.of("/api/companies", "/api/companies/{id}"),
                        "ADMIN"));
    }

}
