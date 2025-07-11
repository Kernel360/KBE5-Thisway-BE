package org.thisway.support.security.config.policy;

import static org.thisway.support.security.config.policy.EndpointRule.permitAll;

import java.util.List;

import org.springframework.http.HttpMethod;

public class ActuatorAuthorizationPolicy {

    public static List<AuthorizationRule> getRules() {
        return List.of(
                permitAll(HttpMethod.GET,
                        List.of("/actuator/**")));
    }
}
