package org.thisway.security.config.policy;

import static org.thisway.security.config.policy.EndpointRule.permitAll;
import static org.thisway.security.config.policy.EndpointRule.withRoles;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

@Configuration
public class RequestAuthorizationPolicy {
    public List<AuthorizationRule> getRules() {
        return List.of(
                // — 비인증(permit all) —
                permitAll(
                        List.of(
                                "/api/auth/login",
                                "/api/auth/verify-code")),
                permitAll(
                        HttpMethod.PUT,
                        List.of("/api/auth/password")),

                // — 사용자 (members) —
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
                        "ADMIN"),

                // — vehicles CRUD —
                // 등록
                withRoles(
                        HttpMethod.POST,
                        List.of("/api/vehicles"),
                        "COMPANY_ADMIN", "COMPANY_CHEF"),
                // 삭제
                withRoles(
                        HttpMethod.DELETE,
                        List.of("/api/vehicles/{id}"),
                        "COMPANY_ADMIN", "COMPANY_CHEF"),
                // 수정
                withRoles(
                        HttpMethod.PATCH,
                        List.of("/api/vehicles/{id}"),
                        "COMPANY_ADMIN", "COMPANY_CHEF"),

                // 조회 (전체/상세): MEMBER, COMPANY_ADMIN, COMPANY_CHEF
                withRoles(
                        List.of(
                                "/api/vehicles",
                                "/api/vehicles/{id}"),
                        "MEMBER", "COMPANY_ADMIN", "COMPANY_CHEF"));
    }

}
