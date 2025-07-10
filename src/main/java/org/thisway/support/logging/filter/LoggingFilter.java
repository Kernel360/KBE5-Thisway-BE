package org.thisway.support.logging.filter;

import java.io.IOException;

import org.springframework.web.filter.OncePerRequestFilter;

import io.micrometer.common.lang.NonNull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LoggingFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String uri = request.getRequestURI();

        return uri.startsWith("/actuator")
                || uri.startsWith("/error")
                || uri.equals("/api/health");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String uri = request.getRequestURI();
        String qs = request.getQueryString();
        String fullUrl = uri + (qs != null ? "?" + qs : "");

        log.info("Request [{} {}]", request.getMethod(), fullUrl);
        try {
            filterChain.doFilter(request, response);
        } finally {
            log.info("Response Status {}", response.getStatus());
        }
    }
}
