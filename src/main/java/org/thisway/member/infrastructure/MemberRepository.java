package org.thisway.member.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.thisway.member.domain.Member;
import org.thisway.member.domain.MemberRole;

import java.util.Optional;
import java.util.Set;

public interface MemberRepository
        extends JpaRepository<Member, Long>, MemberQueryRepository {

    Optional<Member> findByIdAndActiveTrue(Long id);

    Optional<Member> findByEmailAndActiveTrue(String email);

    boolean existsByEmail(String email);

    Page<Member> findAllByActiveTrueAndRoleIn(Set<MemberRole> role, Pageable pageable);

    long countByActiveTrueAndCompanyIdAndRole(long company, MemberRole role);
}
