package org.thisway.member.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.thisway.member.domain.Member;
import org.thisway.member.domain.MemberRole;

import java.util.Collection;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long>, MemberQueryRepository {

    Optional<Member> findByEmailAndActiveTrue(String email);

    Optional<Member> findByEmail(String email);

    boolean existsByEmail(String email);

    Page<Member> findAllByRoleIn(Collection<MemberRole> accessRoles, Pageable pageable);

    long countMemberByCompanyIdAndRole(long companyId, MemberRole role);
}
