package org.thisway.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thisway.auth.dto.VerificationPayload;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;
import org.thisway.member.repository.MemberRepository;

import java.security.SecureRandom;
import java.text.DecimalFormat;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final MemberRepository memberRepository;

    private final StringRedisTemplate redisTemplate;
    private final JavaMailSender javaMailSender;

    @Value("${custom.auth-code-expiration-millis}")
    private long authCodeExpirationMills;

    public void sendVerifyCode(String email) {
        // todo: 이메일 regex 처리 예정.

        // todo: ErrorCode 논의 후 변경 예정.
        memberRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        String code = generateVerifyCode();

        VerificationPayload verificationPayload = new VerificationPayload(code, System.currentTimeMillis() + authCodeExpirationMills);
        storeCode(email, verificationPayload);

        sendMail(email, code);
    }

    public String generateVerifyCode() {
        DecimalFormat CODE_FORMAT = new DecimalFormat("000000");
        SecureRandom secureRandom = new SecureRandom();
        return CODE_FORMAT.format(secureRandom.nextInt(900000) + 100000);
    }

    public void storeCode(String email, VerificationPayload verificationPayload) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            String json = objectMapper.writeValueAsString(verificationPayload);
            redisTemplate.opsForValue().set(email, json, authCodeExpirationMills, TimeUnit.MILLISECONDS);
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
        String html = String.format("""
                <!DOCTYPE html>
                <html lang="ko">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>이메일 인증 코드</title>
                    <style>
                        @import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;600;700&family=Roboto+Mono:wght@700&display=swap');
                
                        body {
                            font-family: 'Inter', sans-serif;
                            margin: 0;
                            padding: 0;
                            background-color: #F9FAFB;
                            color: #111827;
                        }
                
                        .container {
                            max-width: 600px;
                            margin: 0 auto;
                            background-color: #FFFFFF;
                            padding: 40px;
                        }
                
                        .header {
                            text-align: center;
                            margin-bottom: 32px;
                            display: flex;
                            align-items: center;
                            justify-content: center;
                            gap: 12px;
                        }
                
                        .logo {
                            width: 40px;
                            height: 40px;
                        }
                
                        .service-name {
                            font-size: 24px;
                            font-weight: 700;
                            margin: 0;
                        }
                
                        .content {
                            margin-bottom: 32px;
                        }
                
                        .title {
                            font-size: 20px;
                            font-weight: 700;
                            text-align: center;
                            margin-bottom: 8px;
                        }
                
                        .subtitle {
                            font-size: 16px;
                            color: #4B5563;
                            text-align: center;
                            margin-bottom: 24px;
                        }
                
                        .code-container {
                            background-color: #F3F4F6;
                            border-radius: 8px;
                            padding: 24px;
                            text-align: center;
                            margin-bottom: 16px;
                        }
                
                        .code {
                            font-family: 'Roboto Mono', monospace;
                            font-size: 32px;
                            font-weight: 700;
                            letter-spacing: 4px;
                        }
                
                        .expiry {
                            font-size: 14px;
                            color: #6B7280;
                            text-align: center;
                            margin-bottom: 24px;
                        }
                
                        .divider {
                            height: 1px;
                            background-color: #E5E7EB;
                            margin: 24px 0 16px 0;
                        }
                
                        .footer {
                            color: #9CA3AF;
                            font-size: 14px;
                            text-align: center;
                        }
                
                        .footer p {
                            margin: 4px 0;
                        }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <svg class="logo" width="40" height="40" viewBox="0 0 40 40" fill="none" xmlns="http://www.w3.org/2000/svg">
                                <rect width="40" height="40" rx="8" fill="#4F46E5"/>
                                <path d="M20 10L28 18L20 26L12 18L20 10Z" fill="white"/>
                            </svg>
                            <h1 class="service-name">ThisWay</h1>
                        </div>
                
                        <div class="content">
                            <h2 class="title">이메일 인증 코드</h2>
                            <p class="subtitle">아래 인증 코드를 입력하여 이메일 인증을 완료해 주세요.</p>
                
                            <div class="code-container">
                                <div class="code">%s</div>
                            </div>
                            <p class="expiry">인증 코드는 10분 동안 유효합니다.</p>
                
                        </div>
                
                        <div class="divider"></div>
                
                        <div class="footer">
                            <p>본 이메일은 발신 전용이며, 회신하실 수 없습니다.</p>
                            <p>© 2025 Thisway. All rights reserved.</p>
                        </div>
                    </div>
                </body>
                </html>
                """, code);

        return html;
    }
}
