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
import org.thisway.logging.constant.MdcKeys;

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
        MDC.put(MdcKeys.TRACE_ID, UUID.randomUUID().toString());

        try {
            filterChain.doFilter(request, response);

            log.info("Request Body: {}", MDC.get(MdcKeys.REQUEST_BODY));
            log.info("Response Body: {}", MDC.get(MdcKeys.RESPONSE_BODY));
        } finally {
            MDC.clear();
        }
    }
}
