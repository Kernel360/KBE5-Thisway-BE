package org.thisway.security.config.policy.member;

import static org.thisway.security.config.policy.EndpointRule.permitAll;
import static org.thisway.security.config.policy.EndpointRule.withRoles;

import java.util.List;
import org.springframework.http.HttpMethod;
import org.thisway.security.config.policy.AuthorizationRule;

public class MemberAuthorizationPolicy {

    public static List<AuthorizationRule> getRules() {

        // — 사용자 (members) —
        return List.of(
                // 사용자 등록/삭제: ADMIN, COMPANY_CHEF
                withRoles(
                        HttpMethod.POST,
                        List.of("/api/members"),
                        "ADMIN", "COMPANY_CHEF"),

                withRoles(
                        HttpMethod.DELETE,
                        List.of("/api/members/{id}"),
                        "ADMIN", "COMPANY_CHEF"),

                // 사용자 전체 조회: ADMIN, COMPANY_ADMIN, COMPANY_CHEF
                withRoles(
                        HttpMethod.GET,
                        List.of("/api/members"),
                        "ADMIN", "COMPANY_ADMIN", "COMPANY_CHEF"),

                // 사용자 상세 조회: ADMIN, MEMBER, COMPANY_ADMIN, COMPANY_CHEF
                withRoles(
                        HttpMethod.GET,
                        List.of("/api/members/{id}"),
                        "ADMIN", "MEMBER", "COMPANY_ADMIN", "COMPANY_CHEF"),

                // TODO:해당 엔드포인트에 대한 권한 지정이 필요
                permitAll(List.of("/api/members/summary")),

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
                        List.of("/api/admin/members"),
                        "ADMIN"
                )
        );
    }
}
