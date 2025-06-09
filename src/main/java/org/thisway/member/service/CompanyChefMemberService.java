package org.thisway.member.service;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;
import org.thisway.member.dto.CompanyChefMemberDetailOutput;
import org.thisway.member.dto.response.CompanyChefMembersOutput;
import org.thisway.member.entity.Member;
import org.thisway.member.entity.MemberRole;
import org.thisway.member.repository.MemberRepository;
import org.thisway.security.service.SecurityService;

@Service
@RequiredArgsConstructor
@Transactional
public class CompanyChefMemberService {

    private static final Set<MemberRole> COMPANY_CHEF_ACCESS_AUTHORITIES = Set.of(
            MemberRole.COMPANY_CHEF,
            MemberRole.COMPANY_ADMIN,
            MemberRole.MEMBER
    );

    private final SecurityService securityService;

    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public CompanyChefMemberDetailOutput getMemberDetail(Long id) {
        return CompanyChefMemberDetailOutput.from(getActiveMember(id));
    }

    public CompanyChefMembersOutput getMembers(Pageable pageable) {
        long authenticatedMemberCompanyId = securityService.getCurrentMemberDetails().getCompanyId();
        Page<Member> members = memberRepository.findAllByActiveTrueAndRoleInAndCompanyId(
                COMPANY_CHEF_ACCESS_AUTHORITIES, authenticatedMemberCompanyId, pageable
        );

        return CompanyChefMembersOutput.from(members);
    }

    private Member getActiveMember(long id) {
        Member member = memberRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        long authenticatedMemberCompanyId = securityService.getCurrentMemberDetails().getCompanyId();
        if (!COMPANY_CHEF_ACCESS_AUTHORITIES.contains(member.getRole())
                || authenticatedMemberCompanyId != member.getCompany().getId()
        ) {
            throw new CustomException(ErrorCode.MEMBER_ACCESS_DENIED);
        }

        return member;
    }
}
