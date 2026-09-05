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
    void 요청_URI는_기록하지만_query_string은_기록하지_않는다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/trip-log/current/stream/1");
        request.setQueryString("token=secret-access-token&cursor=10");
        MockHttpServletResponse response = new MockHttpServletResponse();

        loggingFilter.doFilter(request, response, new MockFilterChain());

        assertThat(logMessages())
                .contains("Request [GET /api/trip-log/current/stream/1]")
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

    private String logMessages() {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));
    }
}
