package org.thisway.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;

import org.thisway.security.filter.GlobalExceptionHandlerFilter;
import org.thisway.security.filter.JsonAuthenticationFilter;
import org.thisway.security.filter.JwtAuthenticationFilter;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            GlobalExceptionHandlerFilter globalExceptionHandlerFilter,
            JsonAuthenticationFilter jsonAuthenticationFilter,
            JwtAuthenticationFilter jwtAuthenticationFilter

    ) throws Exception {
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
                // 이 부분은 현재 적용 안되고 있을 확률이 높습니다. 추가 테스트 후 리팩토링 할 예정
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
