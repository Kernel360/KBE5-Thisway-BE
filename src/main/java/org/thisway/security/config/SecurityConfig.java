package org.thisway.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.thisway.security.filter.GlobalExceptionHandlerFilter;
import org.thisway.security.filter.JsonAuthenticationFilter;
import org.thisway.security.filter.JwtAuthenticationFilter;
import org.thisway.security.handler.JsonAuthenticationFailureHandler;
import org.thisway.security.handler.JsonAuthenticationSuccessHandler;
import org.thisway.security.service.CustomUserDetailsService;
import org.thisway.security.utils.JwtTokenUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtTokenUtil jwtTokenUtil;
    private final AuthenticationConfiguration authenticationConfiguration;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 이 메서드는 현재 어떻게 작동되는지 이해하지 못하고 넣어둠...
     */
    @Bean
    AuthenticationEntryPoint restAuthEntryPoint() {
        return (request, response, authException) -> {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), authException.getMessage());
        };
    }

    @Bean
    public GlobalExceptionHandlerFilter globalExceptionHandlerFilter(
            ObjectMapper objectMapper) {
        return new GlobalExceptionHandlerFilter(objectMapper);
    }

    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider(
            CustomUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public JsonAuthenticationFilter jsonAuthenticationFilter(
            AuthenticationManager authManager

    ) {
        JsonAuthenticationFilter filter = new JsonAuthenticationFilter(authManager);

        filter.setAuthenticationSuccessHandler(
                new JsonAuthenticationSuccessHandler(jwtTokenUtil));
        filter.setAuthenticationFailureHandler(
                new JsonAuthenticationFailureHandler());
        return filter;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration

    ) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtTokenUtil);
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            GlobalExceptionHandlerFilter globalExceptionHandlerFilter,
            JsonAuthenticationFilter jsonAuthenticationFilter,
            JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS))

                .addFilterBefore(globalExceptionHandlerFilter,
                        SecurityContextHolderFilter.class)
                .addFilterAt(jsonAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class)
                // TODO: 공통 에러 응답 형태로 리팩토링 필요
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler(
                                (
                                        req,
                                        res,
                                        accessEx) -> res.sendError(
                                                HttpStatus.FORBIDDEN.value(),
                                                "Forbidden")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .anyRequest().authenticated())
                .build();
    }

}
