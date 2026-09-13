package org.thisway.member.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.member.infrastructure.MemberRepository;
import org.thisway.support.common.CustomException;
import org.thisway.support.common.ErrorCode;

/** A database-only transaction rechecks the account after the one-use challenge was consumed. */
@Service
@RequiredArgsConstructor
public class PasswordResetPasswordUpdater {
    private final MemberRepository members;

    @Transactional
    public void update(long memberId, String expectedEmail, long expectedCompanyId, String encryptedPassword) {
        var member = members.findActivePasswordResetMemberForUpdate(memberId, expectedEmail, expectedCompanyId)
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_INVALID_VERIFICATION_CODE));
        member.updatePassword(encryptedPassword);
        members.save(member);
    }
}
