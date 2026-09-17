package org.thisway.support.logging.filter;

import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;
import org.thisway.support.logging.TraceContext;
import org.thisway.support.logging.constant.MdcKeys;
import org.slf4j.MDC;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LoggingFilter extends OncePerRequestFilter {
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator") || request.getRequestURI().equals("/api/health");
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        long started = System.nanoTime();
        // Never trust caller-supplied IDs. A valid framework trace may already exist.
        try (var context = TraceContext.open(MDC.get(MdcKeys.TRACE_ID))) {
            response.setHeader("X-Correlation-ID", MDC.get(MdcKeys.TRACE_ID));
            boolean failed = false;
            try { chain.doFilter(request, response); }
            catch (IOException | ServletException | RuntimeException error) { failed = true; throw error; }
            finally {
                Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
                String route = pattern instanceof String value ? value : "UNMATCHED";
                String method = java.util.Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD").contains(request.getMethod())
                        ? request.getMethod() : "OTHER";
                log.info("event=http_dispatch method={} route={} status={} durationMs={} asyncStarted={} failed={}",
                        method, route, failed ? 500 : response.getStatus(),
                        (System.nanoTime() - started) / 1_000_000.0, request.isAsyncStarted(), failed);
            }
        }
    }
}
