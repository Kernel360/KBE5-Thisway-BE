package org.thisway.member.domain;

public interface MemberReader {

    Member requireActiveMemberByEmail(String email);
}
