package org.thisway.member.application;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.thisway.member.domain.Member;
import org.thisway.member.infrastructure.MemberRepository;
import org.thisway.member.util.EmailValidation;
import org.thisway.member.util.PasswordValidation;
import org.thisway.support.common.CustomException;
import org.thisway.support.common.ErrorCode;
import org.thisway.support.component.EmailComponent;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Coordinates external Redis/SMTP work outside the password write transaction. */
@Service
@RequiredArgsConstructor
public class PasswordService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MemberRepository memberRepository;
    private final EmailComponent emailComponent;
    private final PasswordResetChallengeStore challenges;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetPasswordUpdater passwordUpdater;

    public void sendVerificationCode(String email) {
        Member member = activeMember(normalizeEmail(email));
        String issuanceId = UUID.randomUUID().toString();
        String code = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
        var issued = challenges.issue(member.getId(), member.getEmail(), issuanceId, code);
        if (issued == PasswordResetChallengeStore.IssueResult.RATE_LIMITED) throw rateLimited();
        if (issued != PasswordResetChallengeStore.IssueResult.ISSUED) {
            throw new IllegalStateException("Password reset issuance was not acknowledged");
        }
        try {
            // Send to the DB-owned address, not a differently formatted request value.
            emailComponent.sendMail(member.getEmail(), "ThisWay 이메일 인증 코드", "email-content", Map.of("code", code));
        } catch (RuntimeException deliveryFailure) {
            try {
                challenges.cancel(member.getId(), issuanceId);
            } catch (RuntimeException cancellationFailure) {
                // Delivery/cleanup may be uncertain. Never restore a code or refund the request budget.
            }
            throw deliveryFailure;
        }
    }

    public void changePassword(String email, String code, String newPassword) {
        String normalizedEmail = normalizeEmail(email);
        if (code == null || !code.matches("[0-9]{6}")) {
            throw new CustomException(ErrorCode.AUTH_INVALID_VERIFICATION_CODE);
        }
        if (!PasswordValidation.isValidPassword(newPassword)) {
            throw new CustomException(ErrorCode.MEMBER_INVALID_PASSWORD);
        }
        Member member = activeMember(normalizedEmail);
        var outcome = challenges.consume(member.getId(), member.getEmail(), code);
        if (outcome == PasswordResetChallengeStore.ConsumeResult.RATE_LIMITED) throw rateLimited();
        if (outcome != PasswordResetChallengeStore.ConsumeResult.CONSUMED) {
            throw new CustomException(ErrorCode.AUTH_INVALID_VERIFICATION_CODE);
        }
        String encryptedPassword = passwordEncoder.encode(newPassword);
        // The challenge stays consumed on encoder/DB failure or an uncertain commit. Request a new code.
        passwordUpdater.update(member.getId(), member.getEmail(), member.getCompany().getId(), encryptedPassword);
    }

    private Member activeMember(String email) {
        return memberRepository.findActiveLoginMemberByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private String normalizeEmail(String email) {
        String trimmed = email == null ? null : email.trim();
        if (trimmed == null || trimmed.length() > 255 || !EmailValidation.isValidEmail(trimmed)) {
            throw new CustomException(ErrorCode.MEMBER_INVALID_EMAIL);
        }
        // DB collation resolves the account; all limits use its immutable ID.
        return trimmed;
    }

    private static CustomException rateLimited() {
        return new CustomException(ErrorCode.AUTH_VERIFICATION_RATE_LIMITED);
    }
}
