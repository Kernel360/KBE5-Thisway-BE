package org.thisway.member.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;

public interface MemberReader {

    Member getMember(long id);

    MemberInfo.MemberWithCompany getMemberWithCompany(long id);

    Member getMemberByEmail(String email);

    Page<MemberInfo.MemberWithCompany> getMembersWithCompanyByRoleIn(Collection<MemberRole> accessibleRole, Pageable pageable);

    Page<MemberInfo.MemberWithCompany> getMembersWithCompany(MemberQuery.SearchMember memberQuery, Pageable pageable);

    long countMemberByCompanyIdAndRole(long companyId, MemberRole role);

    boolean existByEmail(String email);
}
