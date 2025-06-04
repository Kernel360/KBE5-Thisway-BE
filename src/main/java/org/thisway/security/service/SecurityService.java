package org.thisway.security.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;
import org.thisway.member.entity.Member;
import org.thisway.member.repository.MemberRepository;

@Service
@RequiredArgsConstructor
public class SecurityService {

    private final MemberRepository memberRepository;

    private UserDetails getCurrentUserDetails() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getPrincipal() == null
                || !(authentication.getPrincipal() instanceof UserDetails)
        ) {
            throw new CustomException(ErrorCode.AUTH_UNAUTHENTICATED);
        }

        return (UserDetails) authentication.getPrincipal();
    }

    public Member getCurrentMember() {
        return memberRepository.findByEmailAndActiveTrue(getCurrentUserDetails().getUsername())
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_INVALID_AUTHENTICATION));
    }
}
