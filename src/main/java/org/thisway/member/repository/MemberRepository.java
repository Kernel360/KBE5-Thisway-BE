package org.thisway.member.repository;

import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.thisway.company.entity.Company;
import org.thisway.member.entity.Member;
import org.thisway.member.entity.MemberRole;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByEmailAndActiveTrue(String email);

    boolean existsByEmail(String email);

    Page<Member> findAllByActiveTrue(Pageable pageable);

    Page<Member> findAllByActiveTrueAndRoleInAndCompany(Set<MemberRole> roles, Company company, Pageable pageable);

    long countByActiveTrueAndCompanyIdAndRole(long company, MemberRole role);
}
