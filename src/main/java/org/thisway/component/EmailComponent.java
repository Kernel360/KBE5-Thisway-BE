package org.thisway.component;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class EmailComponent {

    private final JavaMailSender javaMailSender;
    private final TemplateEngine templateEngine;

    public void sendMail(String email, String subject, String templateName, Map<String, Object> variables) {
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();

        try {
            MimeMessageHelper mimeMessageHelper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
            mimeMessageHelper.setTo(email.trim());
            mimeMessageHelper.setSubject(subject);

            mimeMessageHelper.setText(generateEmailContent(templateName, variables), true);
            javaMailSender.send(mimeMessage);

        } catch (MessagingException | MailException | IllegalArgumentException e) {
            throw new CustomException(ErrorCode.SERVER_ERROR);
        }
    }

    private String generateEmailContent(String templateName, Map<String, Object> variables) {
        Context context = new Context();
        context.setVariables(variables);
        return templateEngine.process(templateName, context);
    }
}
