package org.thisway.member.application;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.member.domain.*;
import org.thisway.support.common.CustomException;
import org.thisway.support.common.ErrorCode;

import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberReader memberReader;
    private final MemberStore memberStore;
    private final CompanyClient companyClient;
    private final MemberCommandMapper memberCommandMapper;
    private final MemberQueryMapper memberQueryMapper;

    @Transactional
    public void registerMember(MemberCommand.RegisterMember command, String actorEmail) {
        Member actor = getMemberByEmail(actorEmail);
        Member member = memberCommandMapper.from(command);
        if (!companyClient.existById(member.getCompanyId())) {
            throw new CustomException(ErrorCode.COMPANY_NOT_FOUND);
        }
        if (!actor.canAccess(member)) {
            throw new CustomException(ErrorCode.MEMBER_REGISTER_DENIED);
        }
        assertDuplicateEmail(member.getEmail());
        memberStore.store(member);
    }

    public MemberInfo.MemberWithCompany retrieveMemberWithCompany(long id, String actorEmail) {
        Member actor = getMemberByEmail(actorEmail);
        MemberInfo.MemberWithCompany member = memberReader.getMemberWithCompany(id);
        assertAccess(actor, member);
        return member;
    }

    public Page<MemberInfo.MemberWithCompany> retrieveMembersWithCompany(String actorEmail, Pageable pageable) {
        Member actor = getMemberByEmail(actorEmail);
        Set<MemberRole> accessibleRole = actor.getRole().getAccessibleRoles();
        return memberReader.getMembersWithCompanyByRoleIn(accessibleRole, pageable);
    }

    public Page<MemberInfo.MemberWithCompany> retrieveMembersWithCompany(MemberQuery.SearchMember searchQuery, String actorEmail, Pageable pageable) {
        Member actor = getMemberByEmail(actorEmail);
        Set<MemberRole> accessibleRole = actor.getRole().getAccessibleRoles();
        MemberQuery.SearchMember searchMember = memberQueryMapper.overrideRoles(searchQuery, accessibleRole);
        return memberReader.getMembersWithCompany(searchMember, pageable);
    }

    @Transactional
    public void updateMember(MemberCommand.UpdateMember command, String actorEmail) {
        Member actor = getMemberByEmail(actorEmail);
        Member member = memberReader.getMember(command.getId());
        assertAccess(actor, member);
        assertDuplicateEmail(command.getEmail());

        member.updateName(command.getName());
        member.updateEmail(command.getEmail());
        member.updatePhone(command.getPhone());
        member.updateMemo(command.getMemo());
    }

    @Transactional
    public void deleteMember(long id, String actorEmail) {
        Member actor = getMemberByEmail(actorEmail);
        Member member = memberReader.getMember(id);
        assertAccess(actor, member);
        memberStore.delete(member);
    }

    public MemberInfo.MemberSummary summaryCompanyMember(Long companyId, String actorEmail) {
        Member actor = getMemberByEmail(actorEmail);
        Long actorCompanyId = actor.getCompanyId();
        if (!companyId.equals(actorCompanyId)) {
            throw new CustomException(ErrorCode.COMPANY_ACCESS_DENIED);
        }
        long companyChefCount = countByCompanyAndRole(companyId, MemberRole.COMPANY_CHEF);
        long companyAdminCount = countByCompanyAndRole(companyId, MemberRole.COMPANY_ADMIN);
        long memberCount = countByCompanyAndRole(companyId, MemberRole.MEMBER);

        return MemberInfo.MemberSummary.builder()
                .companyChefCount(companyChefCount)
                .companyAdminCount(companyAdminCount)
                .memberCount(memberCount)
                .build();
    }

    private long countByCompanyAndRole(Long companyId, MemberRole role) {
        return memberReader.countMemberByCompanyIdAndRole(companyId, role);
    }

    private static void assertAccess(Member actor, Member target) {
        if (!actor.canAccess(target)) {
            throw new CustomException(ErrorCode.MEMBER_ACCESS_DENIED);
        }
    }

    private static void assertAccess(Member actor, MemberInfo.MemberWithCompany target) {
        if (!actor.canAccess(target.role(), target.companyInfo().id())) {
            throw new CustomException(ErrorCode.MEMBER_ACCESS_DENIED);
        }
    }

    private void assertDuplicateEmail(String email) {
        if (memberReader.existByEmail(email)) {
            throw new CustomException(ErrorCode.MEMBER_ALREADY_EXIST_BY_EMAIL);
        }
    }

    private Member getMemberByEmail(String email) {
        return memberReader.getMemberByEmail(email);
    }
}
