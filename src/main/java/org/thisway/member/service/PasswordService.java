package org.thisway.member.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;
import org.thisway.component.EmailComponent;
import org.thisway.component.RedisComponent;
import org.thisway.member.dto.VerificationPayload;
import org.thisway.member.entity.Member;
import org.thisway.member.repository.MemberRepository;

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
        // todo: 이메일 regex 처리 예정.

        memberRepository.findByEmailAndActiveTrue(email)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        String code = generateVerificationCode();

        VerificationPayload verificationPayload = new VerificationPayload(code, System.currentTimeMillis() + authCodeExpirationMills);
        redisComponent.storeToRedis(prefix, email, authCodeExpirationMills, verificationPayload);

        Map<String, Object> variables = Map.of("code", code);
        emailComponent.sendMail(email, "ThisWay 이메일 인증 코드", "email-content", variables);
    }

    public void changePassword(String email, String code, String newPassword) {
        // todo: 이메일 regex 처리

        if (verifyCode(email, code)) {
            Member member = memberRepository.findByEmailAndActiveTrue(email)
                    .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
            // todo: 비밀번호 regex 처리

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
