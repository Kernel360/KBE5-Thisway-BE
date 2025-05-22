package org.thisway.member.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.member.dto.request.MemberRegisterRequest;
import org.thisway.member.entity.Member;
import org.thisway.member.repository.MemberRepository;

@Service
@RequiredArgsConstructor
@Transactional
public class MemberService {

    private final MemberRepository memberRepository;

    public void registerMember(MemberRegisterRequest request) {
        Member member = request.toMember();

        memberRepository.save(member);
    }
}
