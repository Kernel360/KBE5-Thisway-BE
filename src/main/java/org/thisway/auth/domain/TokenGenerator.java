package org.thisway.auth.domain;

import org.thisway.member.domain.Member;

public interface TokenGenerator {

    AuthToken generator(Member member);

}
