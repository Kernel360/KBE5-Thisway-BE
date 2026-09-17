package org.thisway.vehicle.log.interfaces;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bounds actual bytes before Jackson materializes telemetry, including chunked bodies. */
@Component
// CORS/security run first so allowed browsers can observe 413; Jackson still runs after this filter.
@Order(org.springframework.boot.autoconfigure.security.SecurityProperties.DEFAULT_FILTER_ORDER + 1)
public class TelemetryBodyLimitFilter extends OncePerRequestFilter {
    public static final int MAX_BYTES = 262_144;
    private static final Set<String> PATHS = Set.of("/api/logs/gps", "/api/logs/power", "/api/logs/geofence");
    private final io.micrometer.core.instrument.MeterRegistry metrics;

    public TelemetryBodyLimitFilter(io.micrometer.core.instrument.MeterRegistry metrics) { this.metrics = metrics; }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !(PATHS.contains(request.getServletPath()) || PATHS.contains(request.getRequestURI().substring(request.getContextPath().length())));
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request.getContentLengthLong() > MAX_BYTES) { reject(response); return; }
        byte[] body = request.getInputStream().readNBytes(MAX_BYTES + 1);
        if (body.length > MAX_BYTES) { reject(response); return; }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override public int getContentLength() { return body.length; }
            @Override public long getContentLengthLong() { return body.length; }
            @Override public ServletInputStream getInputStream() {
                var input = new ByteArrayInputStream(body);
                return new ServletInputStream() {
                    @Override public int read() { return input.read(); }
                    @Override public int read(byte[] bytes, int offset, int length) { return input.read(bytes, offset, length); }
                    @Override public boolean isFinished() { return input.available() == 0; }
                    @Override public boolean isReady() { return true; }
                    @Override public void setReadListener(ReadListener listener) {
                        try { if (!isFinished()) listener.onDataAvailable(); if (isFinished()) listener.onAllDataRead(); }
                        catch (IOException failure) { listener.onError(failure); }
                    }
                };
            }
            @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8)); }
        }, response);
    }

    private void reject(HttpServletResponse response) throws IOException {
        metrics.counter("thisway.telemetry.body.rejected").increment();
        response.setStatus(413);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"15005\",\"message\":\"수집 요청은 256 KiB를 초과할 수 없습니다.\"}");
    }
}
