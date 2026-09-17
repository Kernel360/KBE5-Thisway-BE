package org.thisway.member.application;

/** One challenge per immutable account. Store failures never count as successful verification. */
public interface PasswordResetChallengeStore {
    enum IssueResult { ISSUED, RATE_LIMITED }
    enum ConsumeResult { CONSUMED, INVALID, RATE_LIMITED }

    IssueResult issue(long memberId, String canonicalEmail, String issuanceId, String code);
    ConsumeResult consume(long memberId, String canonicalEmail, String code);

    /** Cancel this issuance only. Request limits are retained even when email delivery fails. */
    void cancel(long memberId, String issuanceId);
}
