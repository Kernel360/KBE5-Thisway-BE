package org.thisway.member.repository;

import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.thisway.member.entity.Member;
import org.thisway.member.entity.MemberRole;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByIdAndActiveTrue(Long id);

    Optional<Member> findByEmailAndActiveTrue(String email);

    boolean existsByEmail(String email);

    Page<Member> findAllByActiveTrueAndRoleIn(Set<MemberRole> role, Pageable pageable);

    Page<Member> findAllByActiveTrueAndRoleInAndCompanyId(Set<MemberRole> roles, long companyId, Pageable pageable);

    long countByActiveTrueAndCompanyIdAndRole(long company, MemberRole role);
}
