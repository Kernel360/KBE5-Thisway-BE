package org.thisway.logging.filter;

import io.micrometer.common.lang.NonNull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Slf4j
public class LoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        MDC.put("traceId", UUID.randomUUID().toString());

        try {
            filterChain.doFilter(request, response);

            log.info("Request Body: {}", MDC.get("requestBody"));
            log.info("Response Body: {}", MDC.get("responseBody"));
        } finally {
            MDC.clear();
        }
    }
}
