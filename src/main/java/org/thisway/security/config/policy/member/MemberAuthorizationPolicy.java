package org.thisway.security.config.policy.member;

import static org.thisway.security.config.policy.EndpointRule.withRoles;

import java.util.List;
import org.springframework.http.HttpMethod;
import org.thisway.security.config.policy.AuthorizationRule;

public class MemberAuthorizationPolicy {

    public static List<AuthorizationRule> getRules() {

        // — 사용자 (members) —
        return List.of(
                withRoles(
                        HttpMethod.GET,
                        List.of("/api/admin/members/{id}"),
                        "ADMIN"
                ),

                withRoles(
                        HttpMethod.GET,
                        List.of("/api/admin/members"),
                        "ADMIN"
                ),

                withRoles(
                        HttpMethod.POST,
                        List.of("/api/admin/members"),
                        "ADMIN"
                ),

                withRoles(
                        HttpMethod.PUT,
                        List.of("/api/admin/members/{id}"),
                        "ADMIN"
                ),

                withRoles(
                        HttpMethod.DELETE,
                        List.of("/api/admin/members/{id}"),
                        "ADMIN"
                ),

                withRoles(
                        HttpMethod.GET,
                        List.of("/api/company-chef/members/{id}"),
                        "COMPANY_CHEF"
                ),

                withRoles(
                        HttpMethod.GET,
                        List.of("/api/company-chef/members"),
                        "COMPANY_CHEF"
                ),

                withRoles(
                        HttpMethod.POST,
                        List.of("/api/company-chef/members"),
                        "COMPANY_CHEF"
                ),

                withRoles(
                        HttpMethod.PUT,
                        List.of("/api/company-chef/members/{id}"),
                        "COMPANY_CHEF"
                ),

                withRoles(
                        HttpMethod.DELETE,
                        List.of("/api/company-chef/members"),
                        "COMPANY_CHEF"
                ),

                withRoles(
                        HttpMethod.DELETE,
                        List.of("/api/company-chef/members/summary"),
                        "COMPANY_CHEF"
                )
        );
    }
}
