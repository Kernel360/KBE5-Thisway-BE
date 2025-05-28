package org.thisway.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.auth.dto.VerificationPayload;
import org.thisway.auth.dto.request.PasswordChangeRequest;
import org.thisway.auth.dto.request.SendVerifyCodeRequest;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;
import org.thisway.member.entity.Member;
import org.thisway.member.repository.MemberRepository;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.security.SecureRandom;
import java.text.DecimalFormat;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final MemberRepository memberRepository;

    private final StringRedisTemplate redisTemplate;
    private final JavaMailSender javaMailSender;
    private final ObjectMapper objectMapper;

    private final TemplateEngine templateEngine;

    @Value("${custom.auth-code-expiration-millis}")
    private long authCodeExpirationMills;

    @Transactional(readOnly = true)
    public void sendVerificationCode(SendVerifyCodeRequest request) {
        // todo: 이메일 regex 처리 예정.

        // todo: ErrorCode 논의 후 변경 예정.
        memberRepository.findByEmailAndActiveTrue(request.email())
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        String code = generateVerificationCode();

        VerificationPayload verificationPayload = new VerificationPayload(code, System.currentTimeMillis() + authCodeExpirationMills);
        storeCode(request.email(), verificationPayload);

        sendMail(request.email(), code);
    }

    @Transactional
    public void changePassword(PasswordChangeRequest request) {
        // todo: 이메일 regex 처리

        if (verifyCode(request.email(), request.code())) {
            Member member = memberRepository.findByEmailAndActiveTrue(request.email())
                    .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
            // todo: 비밀번호 regex 처리

            member.updatePassword(request.newPassword());
            memberRepository.save(member);
            redisTemplate.delete(request.email());
        } else {
            throw new CustomException(ErrorCode.INVALID_VERIFY_CODE);
        }
    }

    public String generateVerificationCode() {
        DecimalFormat CODE_FORMAT = new DecimalFormat("000000");
        SecureRandom secureRandom = new SecureRandom();
        return CODE_FORMAT.format(secureRandom.nextInt(900000) + 100000);
    }

    public void storeCode(String email, VerificationPayload verificationPayload) {
        try {
            String json = objectMapper.writeValueAsString(verificationPayload);
            redisTemplate.opsForValue().set(email, json, authCodeExpirationMills, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new CustomException(ErrorCode.SERVER_ERROR);
        }
    }

    public Boolean verifyCode(String email, String code) {
        VerificationPayload savedEntry = retrieveFromRedis(email);

        if (savedEntry == null || savedEntry.isExpired()) {
            redisTemplate.delete(email);
            return false;
        }

        return savedEntry.code().equals(code);
    }

    public VerificationPayload retrieveFromRedis(String email) {
        try {
            String savedCode = redisTemplate.opsForValue().get(email);

            if (savedCode != null && !savedCode.isBlank()) {
                return objectMapper.readValue(savedCode, VerificationPayload.class);
            } else return null;
        } catch (Exception e) {
            throw new CustomException(ErrorCode.SERVER_ERROR);
        }
    }

    public void sendMail(String email, String code) {
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();

        try {
            MimeMessageHelper mimeMessageHelper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
            mimeMessageHelper.setTo(email.trim());
            mimeMessageHelper.setSubject("ThisWay 비밀번호 변경 인증 메일");

            mimeMessageHelper.setText(generateEmailContent(code), true);
            javaMailSender.send(mimeMessage);
        } catch (MessagingException | MailException e) {
            throw new CustomException(ErrorCode.SERVER_ERROR);
        }
    }

    public String generateEmailContent(String code) {
        Context context = new Context();
        context.setVariable("code", code);
        return templateEngine.process("email-content", context);
    }
}
