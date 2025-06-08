package org.thisway.member.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.member.dto.MembersDto;
import org.thisway.member.entity.Member;
import org.thisway.member.entity.MemberRole;
import org.thisway.member.repository.MemberRepository;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminMemberService {

    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public MembersDto getMembers(Pageable pageable) {
        Page<Member> members = memberRepository.findAllByActiveTrueAndRole(MemberRole.COMPANY_CHEF, pageable);

        return MembersDto.from(members);
    }
}
