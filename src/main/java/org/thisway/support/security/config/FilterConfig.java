package org.thisway.support.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;

import org.thisway.support.security.handler.JsonAuthenticationFailureHandler;
import org.thisway.support.security.handler.JsonAuthenticationSuccessHandler;
import org.thisway.support.security.utils.JwtTokenUtil;
import org.thisway.support.logging.filter.LoggingFilter;
import org.thisway.support.security.filter.GlobalExceptionHandlerFilter;
import org.thisway.support.security.filter.JsonAuthenticationFilter;
import org.thisway.support.security.filter.JwtAuthenticationFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class FilterConfig {
    @Bean
    public LoggingFilter loggingFilter() {
        return new LoggingFilter();
    }

    @Bean
    public GlobalExceptionHandlerFilter globalExceptionHandlerFilter(
            ObjectMapper objectMapper

    ) {
        return new GlobalExceptionHandlerFilter(objectMapper);
    }

    @Bean
    public JsonAuthenticationFilter jsonAuthenticationFilter(
            AuthenticationManager authManager,
            JwtTokenUtil jwtTokenUtil

    ) {
        JsonAuthenticationFilter filter = new JsonAuthenticationFilter(authManager);

        filter.setAuthenticationSuccessHandler(
                new JsonAuthenticationSuccessHandler(jwtTokenUtil));
        filter.setAuthenticationFailureHandler(
                new JsonAuthenticationFailureHandler());
        return filter;
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(
            JwtTokenUtil jwtTokenUtil

    ) {
        return new JwtAuthenticationFilter(jwtTokenUtil);
    }
}
