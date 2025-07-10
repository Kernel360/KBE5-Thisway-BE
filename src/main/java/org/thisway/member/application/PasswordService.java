package org.thisway.member.application;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.support.common.CustomException;
import org.thisway.support.common.ErrorCode;
import org.thisway.support.component.EmailComponent;
import org.thisway.support.component.RedisComponent;
import org.thisway.member.domain.Member;
import org.thisway.member.infrastructure.MemberRepository;
import org.thisway.member.util.EmailValidation;
import org.thisway.member.util.PasswordValidation;

import java.security.SecureRandom;
import java.text.DecimalFormat;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class PasswordService {

    private final MemberRepository memberRepository;

    private final EmailComponent emailComponent;
    private final RedisComponent redisComponent;
    private final PasswordEncoder passwordEncoder;

    @Value("${custom.verification-code-expiration-millis}")
    private long authCodeExpirationMills;
    @Value("${custom.verification-code-key-prefix}")
    private String prefix;

    @Transactional(readOnly = true)
    public void sendVerificationCode(String email) {
        if (!EmailValidation.isValidEmail(email)) {
            throw new CustomException(ErrorCode.MEMBER_INVALID_EMAIL);
        }

        memberRepository.findByEmailAndActiveTrue(email)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        String code = generateVerificationCode();

        VerificationPayload verificationPayload = new VerificationPayload(code,
                System.currentTimeMillis() + authCodeExpirationMills);
        redisComponent.storeToRedis(prefix, email, authCodeExpirationMills, verificationPayload);

        Map<String, Object> variables = Map.of("code", code);
        emailComponent.sendMail(email, "ThisWay 이메일 인증 코드", "email-content", variables);
    }

    public void changePassword(String email, String code, String newPassword) {
        if (!EmailValidation.isValidEmail(email)) {
            throw new CustomException(ErrorCode.MEMBER_INVALID_EMAIL);
        }

        if (verifyCode(email, code)) {
            Member member = memberRepository.findByEmailAndActiveTrue(email)
                    .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

            if (!PasswordValidation.isValidPassword(newPassword)) {
                throw new CustomException(ErrorCode.MEMBER_INVALID_PASSWORD);
            }

            String encryptedPassword = passwordEncoder.encode(newPassword);
            member.updatePassword(encryptedPassword);
            memberRepository.save(member);

            redisComponent.delete(prefix, email);
        } else {
            throw new CustomException(ErrorCode.AUTH_INVALID_VERIFICATION_CODE);
        }
    }

    private String generateVerificationCode() {
        DecimalFormat CODE_FORMAT = new DecimalFormat("000000");
        SecureRandom secureRandom = new SecureRandom();
        return CODE_FORMAT.format(secureRandom.nextInt(900000) + 100000);
    }

    private Boolean verifyCode(String email, String code) {
        VerificationPayload savedEntry = redisComponent.retrieveFromRedis(prefix, email, VerificationPayload.class);

        if (savedEntry == null || savedEntry.isExpired()) {
            redisComponent.delete(prefix, email);
            return false;
        }

        return savedEntry.code().equals(code);
    }

}
