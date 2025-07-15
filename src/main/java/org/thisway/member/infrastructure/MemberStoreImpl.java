package org.thisway.member.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.member.domain.Member;
import org.thisway.member.domain.MemberStore;

@Component
@RequiredArgsConstructor
@Transactional
public class MemberStoreImpl implements MemberStore {

    private final MemberRepository memberRepository;

    @Override
    public void store(Member member) {
        memberRepository.save(member);
    }

    @Override
    public void delete(Member member) {
        memberRepository.delete(member);
    }
}
