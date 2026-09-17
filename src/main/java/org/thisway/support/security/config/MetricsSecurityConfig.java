package org.thisway.support.security.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import jakarta.servlet.http.HttpServletRequest;

/** A scrape credential grants access to one read-only endpoint, never to human APIs. */
@Configuration
public class MetricsSecurityConfig {
    @Bean
    @Order(1)
    SecurityFilterChain metricsFilterChain(HttpSecurity http,
            @Value("${thisway.metrics.token-sha256:}") String configuredDigest) throws Exception {
        var credential = new ScrapeCredential(configuredDigest);
        return http.securityMatcher(EndpointRequest.to("prometheus"))
                .csrf(AbstractHttpConfigurer::disable).cors(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, exception) -> response.setStatus(403)))
                .authorizeHttpRequests(rules -> rules.anyRequest().access((authentication, context) ->
                        new AuthorizationDecision(credential.permits(context.getRequest()))))
                .build();
    }

    static final class ScrapeCredential {
        private final byte[] expected;
        ScrapeCredential(String digest) {
            if (!digest.isEmpty() && !digest.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("Metrics token digest must be empty or lowercase SHA-256 hex");
            expected = digest.isEmpty() ? null : HexFormat.of().parseHex(digest);
        }
        boolean permits(HttpServletRequest request) {
            if (expected == null || !"GET".equals(request.getMethod())) return false;
            var headers = request.getHeaders("Authorization");
            if (headers == null || !headers.hasMoreElements()) return false;
            String header = headers.nextElement();
            if (headers.hasMoreElements() || header.length() != 71 || !header.startsWith("Bearer ")) return false;
            String token = header.substring(7);
            if (!token.matches("[0-9a-f]{64}")) return false;
            try {
                return MessageDigest.isEqual(expected, MessageDigest.getInstance("SHA-256")
                        .digest(token.getBytes(StandardCharsets.US_ASCII)));
            } catch (NoSuchAlgorithmException unavailable) { throw new IllegalStateException("SHA-256 unavailable"); }
        }
    }
}
