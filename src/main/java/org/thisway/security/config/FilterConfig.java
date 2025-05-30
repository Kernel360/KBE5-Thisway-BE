package org.thisway.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;

import org.thisway.security.handler.JsonAuthenticationFailureHandler;
import org.thisway.security.handler.JsonAuthenticationSuccessHandler;
import org.thisway.security.utils.JwtTokenUtil;
import org.thisway.security.filter.GlobalExceptionHandlerFilter;
import org.thisway.security.filter.JsonAuthenticationFilter;
import org.thisway.security.filter.JwtAuthenticationFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class FilterConfig {

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
