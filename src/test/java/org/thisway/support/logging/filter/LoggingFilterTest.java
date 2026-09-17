package org.thisway.support.logging.filter;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.slf4j.LoggerFactory;

import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingFilterTest {

    private final LoggingFilter loggingFilter = new LoggingFilter();
    private final Logger logger = (Logger) LoggerFactory.getLogger(LoggingFilter.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void setUp() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void 요청_경로와_query를_버리고_서버_패턴만_기록한다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/trip-log/current/stream/1");
        request.setQueryString("token=secret-access-token&cursor=10");
        MockHttpServletResponse response = new MockHttpServletResponse();

        loggingFilter.doFilter(request, response, (req, res) -> req.setAttribute(
                org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/trip-log/current/stream/{id}"));

        assertThat(logMessages())
                .contains("route=/api/trip-log/current/stream/{id}")
                .doesNotContain("/stream/1")
                .doesNotContain("secret-access-token")
                .doesNotContain("token=")
                .doesNotContain("cursor=10");
    }

    @Test
    void actuator_요청은_access_log에서_제외한다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        loggingFilter.doFilter(request, response, new MockFilterChain());

        assertThat(logMessages()).doesNotContain("/actuator/health");
    }

    @Test
    void 악성_header와_미매칭_path를_기록하지_않고_MDC를_복원한다() throws Exception {
        org.slf4j.MDC.put("traceId", "parent-context");
        try {
            var request = new MockHttpServletRequest("GET", "/secret-coordinate-token");
            request.addHeader("X-Correlation-ID", "injected-secret");
            var response = new MockHttpServletResponse();
            loggingFilter.doFilter(request, response, (req, res) -> {
                assertThat(org.slf4j.MDC.get("traceId")).matches("[0-9a-f]{32}");
                assertThat(response.getHeader("X-Correlation-ID")).isEqualTo(org.slf4j.MDC.get("traceId"));
            });
            assertThat(logMessages()).contains("route=UNMATCHED").doesNotContain("secret", "parent-context");
            assertThat(org.slf4j.MDC.get("traceId")).isEqualTo("parent-context");
        } finally { org.slf4j.MDC.clear(); }
    }

    @Test
    void 실패해도_추적값이_스레드에_남지_않는다() {
        var request = new MockHttpServletRequest("GET", "/private");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> loggingFilter.doFilter(request,
                new MockHttpServletResponse(), (req, res) -> { throw new IllegalStateException("secret"); }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(org.slf4j.MDC.get("traceId")).isNull();
        assertThat(logMessages()).contains("status=500").doesNotContain("secret");
    }

    private String logMessages() {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));
    }
}
