package org.thisway.member.repository;

import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.thisway.member.entity.Member;
import org.thisway.member.entity.MemberRole;
import org.thisway.member.service.dto.CompanyChefMemberSearchCriteria;

public interface MemberQueryRepository {

    Page<Member> searchActiveMembers(
            Set<MemberRole> role,
            Long companyId,
            CompanyChefMemberSearchCriteria criteria,
            Pageable pageable
    );
}
