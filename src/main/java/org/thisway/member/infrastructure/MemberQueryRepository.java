package org.thisway.member.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.thisway.member.application.CompanyChefMemberSearchCriteria;
import org.thisway.member.domain.Member;
import org.thisway.member.domain.MemberRole;

import java.util.Set;

public interface MemberQueryRepository {

    Page<Member> searchActiveMembers(
            Set<MemberRole> role,
            Long companyId,
            CompanyChefMemberSearchCriteria criteria,
            Pageable pageable
    );
}
