package org.thisway.member.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.thisway.member.domain.Member;
import org.thisway.member.domain.MemberRole;

import java.util.Collection;

public interface MemberQueryRepository {

    Page<Member> searchMembers(
            Collection<MemberRole> role,
            long companyId,
            String memberName,
            Pageable pageable
    );
}
