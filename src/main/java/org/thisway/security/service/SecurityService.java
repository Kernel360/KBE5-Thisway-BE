package org.thisway.security.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;
import org.thisway.member.domain.Member;
import org.thisway.member.infrastructure.MemberRepository;
import org.thisway.security.dto.request.MemberDetails;

@Service
@RequiredArgsConstructor
@Transactional
public class SecurityService {

    private final MemberRepository memberRepository;

    public MemberDetails getCurrentMemberDetails() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getPrincipal() == null
                || !(authentication.getPrincipal() instanceof MemberDetails memberDetails)
        ) {
            throw new CustomException(ErrorCode.AUTH_UNAUTHENTICATED);
        }

        return memberDetails;
    }

    @Transactional(readOnly = true)
    public Member getCurrentMember() {
        return memberRepository.findByEmailAndActiveTrue(getCurrentMemberDetails().getUsername())
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_INVALID_AUTHENTICATION));
    }
}
