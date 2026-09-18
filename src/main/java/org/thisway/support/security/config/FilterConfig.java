package org.thisway.support.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thisway.support.logging.filter.LoggingFilter;
import org.thisway.support.security.filter.GlobalExceptionHandlerFilter;
import org.thisway.support.security.filter.JwtAuthenticationFilter;
import org.thisway.support.security.utils.JwtTokenProvider;

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
    public JwtAuthenticationFilter jwtAuthenticationFilter(
            JwtTokenProvider jwtTokenUtil
    ) {
        return new JwtAuthenticationFilter(jwtTokenUtil);
    }
}
