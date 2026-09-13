package org.thisway.member.infrastructure;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.member.domain.Member;
import org.thisway.member.domain.MemberReader;
import org.thisway.member.domain.MemberRole;
import org.thisway.support.common.CustomException;
import org.thisway.support.common.ErrorCode;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberReaderImpl implements MemberReader {

    private final MemberRepository memberRepository;

    @Override
    public Member requireActiveMemberByEmail(String email) {
        return memberRepository.findActiveLoginMemberByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_MEMBER_NOT_FOUND));
    }

    @Override
    public boolean isCurrentIdentity(long memberId, String email, long companyId, MemberRole role) {
        return memberRepository.existsCurrentIdentity(memberId, email, companyId, role);
    }
}
