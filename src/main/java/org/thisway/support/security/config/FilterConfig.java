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
    // These filters belong to the application SecurityFilterChain only. Automatic servlet
    // registration would run the JWT parser on valid opaque Prometheus credentials.
    @Bean
    org.springframework.boot.web.servlet.FilterRegistrationBean<LoggingFilter> loggingRegistration(LoggingFilter filter) {
        return disabled(filter);
    }
    @Bean
    org.springframework.boot.web.servlet.FilterRegistrationBean<JwtAuthenticationFilter> jwtRegistration(JwtAuthenticationFilter filter) {
        return disabled(filter);
    }
    @Bean
    org.springframework.boot.web.servlet.FilterRegistrationBean<GlobalExceptionHandlerFilter> errorRegistration(GlobalExceptionHandlerFilter filter) {
        return disabled(filter);
    }
    private static <T extends jakarta.servlet.Filter> org.springframework.boot.web.servlet.FilterRegistrationBean<T> disabled(T filter) {
        var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

}
