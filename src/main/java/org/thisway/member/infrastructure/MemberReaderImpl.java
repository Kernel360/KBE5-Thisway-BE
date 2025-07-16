package org.thisway.member.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.member.domain.*;
import org.thisway.support.common.CustomException;
import org.thisway.support.common.ErrorCode;

import java.util.Collection;
import java.util.List;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberReaderImpl implements MemberReader {

    private final MemberRepository memberRepository;
    private final CompanyClient companyClient;
    private final MemberInfoMapper memberInfoMapper;

    @Override
    public Member getMember(long id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
    }

    @Override
    public MemberInfo.MemberWithCompany getMemberWithCompany(long id) {
        Member member = getMember(id);
        CompanyInfo companyInfo = companyClient.getById(member.getCompanyId());
        return memberInfoMapper.toMemberInfo(member, companyInfo);
    }

    @Override
    public Member getMemberByEmail(String email) {
        return memberRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
    }

    @Override
    public Page<MemberInfo.MemberWithCompany> getMembersWithCompanyByRoleIn(Collection<MemberRole> roles, Pageable pageable) {
        Page<Member> members = memberRepository.findAllByRoleIn(roles, pageable);
        List<CompanyInfo> companies = companyClient.findAllByMember(members.toList());
        return memberInfoMapper.toMemberInfos(members, companies);
    }

    @Override
    public Page<MemberInfo.MemberWithCompany> getMembersWithCompany(MemberQuery.SearchMember memberQuery, Pageable pageable) {
        Page<Member> members = memberRepository.searchMembers(
                memberQuery.roles(),
                memberQuery.companyId(),
                memberQuery.name(),
                pageable
        );
        List<CompanyInfo> companies = companyClient.findAllByMember(members.toList());
        return memberInfoMapper.toMemberInfos(members, companies);
    }

    @Override
    public long countMemberByCompanyIdAndRole(long companyId, MemberRole role) {
        return memberRepository.countMemberByCompanyIdAndRole(companyId, role);
    }

    @Override
    public boolean existByEmail(String email) {
        return memberRepository.existsByEmail(email);
    }
}
