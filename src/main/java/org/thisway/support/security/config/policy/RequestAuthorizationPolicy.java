package org.thisway.support.security.config.policy;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thisway.support.security.config.policy.auth.AuthAuthorizationPolicy;
import org.thisway.support.security.config.policy.company.CompanyAuthorizationPolicy;
import org.thisway.support.security.config.policy.emulator.EmulatorAuthorizationPolicy;
import org.thisway.support.security.config.policy.member.MemberAuthorizationPolicy;
import org.thisway.support.security.config.policy.statistics.StatisticsAuthorizationPolicy;
import org.thisway.support.security.config.policy.triplog.TripLogAuthorizationPolicy;
import org.thisway.support.security.config.policy.vehicle.VehicleAuthorizationPolicy;

@Configuration
public class RequestAuthorizationPolicy {

    @Bean
    public List<AuthorizationRule> getRules() {
        List<AuthorizationRule> rules = new ArrayList<>();

        rules.addAll(AuthAuthorizationPolicy.getRules());
        rules.addAll(MemberAuthorizationPolicy.getRules());
        rules.addAll(CompanyAuthorizationPolicy.getRules());
        rules.addAll(VehicleAuthorizationPolicy.getRules());
        rules.addAll(EmulatorAuthorizationPolicy.getRules());
        rules.addAll(ActuatorAuthorizationPolicy.getRules());
        rules.addAll(TripLogAuthorizationPolicy.getRules());
        rules.addAll(StatisticsAuthorizationPolicy.getRules());

        return rules;
    }
}
