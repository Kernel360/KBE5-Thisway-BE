package org.thisway.support.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.thisway.support.security.filter.GlobalExceptionHandlerFilter;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import static org.assertj.core.api.Assertions.assertThat;
class SecurityErrorPrivacyTest {
    @Test void 인증_오류_원문이_로그와_HTTP에_포함되지_않는다() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandlerFilter.class);
        var appender = new ListAppender<ILoggingEvent>(); appender.start(); logger.addAppender(appender);
        try {
            var response = new MockHttpServletResponse();
            new GlobalExceptionHandlerFilter(new ObjectMapper()).doFilter(new MockHttpServletRequest(), response,
                    (req, res) -> { throw new io.jsonwebtoken.JwtException("secret-token-37.123-127.123"); });
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getContentAsString()).doesNotContain("secret-token", "37.123");
            assertThat(appender.list).allSatisfy(event -> {
                assertThat(event.getFormattedMessage()).doesNotContain("secret-token", "37.123");
                assertThat(event.getThrowableProxy()).isNull();
            });
        } finally { logger.detachAppender(appender); appender.stop(); }
    }
    @Test void Rabbit_custom_message와_메시지_body도_로그에서_제외한다() {
        Logger logger = (Logger) LoggerFactory.getLogger(org.thisway.support.common.RabbitMqGlobalErrorHandler.class);
        var appender = new ListAppender<ILoggingEvent>(); appender.start(); logger.addAppender(appender);
        try {
            var failure = new org.springframework.amqp.rabbit.support.ListenerExecutionFailedException("secret-envelope",
                    new org.thisway.support.common.CustomException(org.thisway.support.common.ErrorCode.SERVER_ERROR, "secret-custom"),
                    new org.springframework.amqp.core.Message("secret-body".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                            new org.springframework.amqp.core.MessageProperties()));
            new org.thisway.support.common.RabbitMqGlobalErrorHandler().handleError(failure);
            assertThat(appender.list).isNotEmpty().allSatisfy(event -> {
                assertThat(event.getFormattedMessage()).doesNotContain("secret-envelope", "secret-custom", "secret-body");
                assertThat(event.getThrowableProxy()).isNull();
            });
        } finally { logger.detachAppender(appender); appender.stop(); }
    }

}
