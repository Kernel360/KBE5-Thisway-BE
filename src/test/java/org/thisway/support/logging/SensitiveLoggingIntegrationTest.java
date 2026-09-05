package org.thisway.support.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.thisway.auth.application.AuthCommand;
import org.thisway.auth.application.AuthInfo;
import org.thisway.auth.application.AuthService;
import org.thisway.member.application.PasswordService;

import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.security.debug=false",
        "logging.level.org.springframework.security=INFO",
        "logging.level.org.springframework.web=INFO"
})
@AutoConfigureMockMvc
class SensitiveLoggingIntegrationTest {

    private static final String PASSWORD = "SecretPassword!123";
    private static final String ACCESS_TOKEN = "secret-access-token";
    private static final String REFRESH_TOKEN = "secret-refresh-token";
    private static final String VERIFICATION_CODE = "839201";
    private static final String NEW_PASSWORD = "NewSecretPassword!456";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private PasswordService passwordService;

    @Test
    void 로그인_request와_response의_비밀번호와_토큰을_로그에_남기지_않는다() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        when(authService.login(any(AuthCommand.LoginRequest.class))).thenReturn(
                AuthInfo.LoginResult.builder()
                        .accessToken(ACCESS_TOKEN)
                        .refreshToken(REFRESH_TOKEN)
                        .build()
        );

        try {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "email": "member@example.com",
                                      "password": "%s"
                                    }
                                    """.formatted(PASSWORD)))
                    .andExpect(status().isOk());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        String logMessages = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));

        assertThat(logMessages)
                .contains("Request [POST /api/auth/login]")
                .doesNotContain(PASSWORD)
                .doesNotContain(ACCESS_TOKEN)
                .doesNotContain(REFRESH_TOKEN);
    }

    @Test
    void 비밀번호_변경의_인증코드와_새_비밀번호를_로그에_남기지_않는다() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            mockMvc.perform(put("/api/auth/password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "email": "member@example.com",
                                      "code": "%s",
                                      "newPassword": "%s"
                                    }
                                    """.formatted(VERIFICATION_CODE, NEW_PASSWORD)))
                    .andExpect(status().isOk());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        String logMessages = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));

        assertThat(logMessages)
                .contains("Request [PUT /api/auth/password]")
                .doesNotContain(VERIFICATION_CODE)
                .doesNotContain(NEW_PASSWORD);
    }
}
