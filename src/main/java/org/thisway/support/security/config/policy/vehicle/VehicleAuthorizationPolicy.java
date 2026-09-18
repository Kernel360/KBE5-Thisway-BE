package org.thisway.support.security.config.policy.vehicle;

import static org.thisway.support.security.config.policy.EndpointRule.withRoles;

import java.util.List;

import org.springframework.http.HttpMethod;
import org.thisway.support.security.config.policy.AuthorizationRule;

public class VehicleAuthorizationPolicy {

    public static List<AuthorizationRule> getRules() {
        return List.of(
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

                // 조회 (전체/상세/대시보드): MEMBER, COMPANY_ADMIN, COMPANY_CHEF
                withRoles(
                        HttpMethod.GET,
                        List.of(
                                "/api/vehicles",
                                "/api/vehicles/{id}",
                                "/api/vehicles/dashboard"),
                        "MEMBER", "COMPANY_ADMIN", "COMPANY_CHEF"));
    }
}
