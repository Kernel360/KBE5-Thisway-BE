package org.thisway.member.infrastructure;

import org.springframework.stereotype.Component;
import org.thisway.member.domain.Member;
import org.thisway.member.domain.MemberReader;
import org.thisway.support.common.CustomException;
import org.thisway.support.common.ErrorCode;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MemberReaderImpl implements MemberReader {

    private final MemberRepository memberRepository;

    @Override
    public Member requireActiveMemberByEmail(String email) {
        return memberRepository.findByEmailAndActiveTrue(email)
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_MEMBER_NOT_FOUND));
    }
}
