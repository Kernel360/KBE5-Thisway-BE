package org.thisway.member.domain;

public interface MemberReader {

    Member requireActiveMemberByEmail(String email);

    boolean isCurrentIdentity(long memberId, String email, long companyId, MemberRole role);
}
