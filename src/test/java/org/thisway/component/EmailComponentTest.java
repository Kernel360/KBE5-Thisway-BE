package org.thisway.component;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {EmailComponent.class})
@RequiredArgsConstructor
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
public class EmailComponentTest {

    @InjectMocks
    private final EmailComponent emailComponent;

    @MockitoBean
    private final JavaMailSender javaMailSender;
    @MockitoBean
    private final TemplateEngine templateEngine;
    @MockitoBean
    private final MimeMessage mimeMessage;
    @MockitoBean
    private final MimeMessageHelper mimeMessageHelper;

    private String email = "abc@example.com";
    private final String subject = "메일 제목";
    private final String templateName = "template-name";
    private final Map<String, Object> variables = Map.of("code", "123456");
    private final String expectedContent = "메일 본문 입니다.";

    @Test
    @DisplayName("정상적으로 메일이 발송된다.")
    void 메일_정상_발송() {
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(eq(templateName),
                any(Context.class))).thenReturn(expectedContent);

        assertThatCode(() -> emailComponent.sendMail(email, subject, templateName, variables))
                .doesNotThrowAnyException();

        verify(javaMailSender).send(mimeMessage);
        verify(templateEngine).process(eq(templateName), any(Context.class));
    }

    @Test
    @DisplayName("메일 발송 과정에서 MailException 에러 발생 시, server_error 응답을 한다.")
    void MailException_에러_발생시_SERVER_ERROR_응답() {
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(eq(templateName),
                any(Context.class))).thenReturn(expectedContent);
        doThrow(new MailSendException("메일 전송 실패"))
                .when(javaMailSender).send(any(MimeMessage.class));

        CustomException e = assertThrows(CustomException.class, () -> emailComponent.sendMail(email,  subject, templateName, variables));

        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SERVER_ERROR);
    }

    @Test
    @DisplayName("메일 발송 과정에서 MessagingException 에러 발생 시, server_error 응답을 한다.")
    void MessagingException_에러_발생시_SERVER_ERROR_응답() throws Exception {
        email = " ";      // 메일 주소가 없다면 MessagingException 에러 발생

        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(eq(templateName),
                any(Context.class))).thenReturn(expectedContent);

        CustomException e = assertThrows(CustomException.class, () -> emailComponent.sendMail(email,  subject, templateName, variables));

        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SERVER_ERROR);
    }

    @Test
    @DisplayName("메일 발송 시 올바르지 않은 Template를 입력한다면, server_error 응답을 한다.")
    void 없는_메일_템플릿_입력시_SERVER_ERROR_응답() {
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

        CustomException e = assertThrows(CustomException.class, () -> emailComponent.sendMail(email,  subject, templateName, variables));

        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SERVER_ERROR);
    }

}
