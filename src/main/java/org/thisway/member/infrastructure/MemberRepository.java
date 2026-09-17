package org.thisway.member.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.thisway.member.domain.Member;
import org.thisway.member.domain.MemberRole;

import java.util.Optional;
import java.util.Set;

public interface MemberRepository
        extends JpaRepository<Member, Long>, MemberQueryRepository {

    Optional<Member> findByIdAndActiveTrue(Long id);

    Optional<Member> findByIdAndCompanyIdAndActiveTrue(Long id, Long companyId);

    Optional<Member> findByEmailAndActiveTrue(String email);

    @Query("""
            select m from Member m join fetch m.company c
            where m.email = :email and m.active = true and c.active = true
            """)
    Optional<Member> findActiveLoginMemberByEmail(@Param("email") String email);

    @Query("""
            select (count(m) > 0) from Member m join m.company c
            where m.id = :memberId and m.email = :email and m.role = :role
              and m.active = true and c.id = :companyId and c.active = true
            """)
    boolean existsCurrentIdentity(@Param("memberId") long memberId, @Param("email") String email,
                                  @Param("companyId") long companyId, @Param("role") MemberRole role);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select m from Member m join fetch m.company c
            where m.id = :memberId and m.email = :email and c.id = :companyId
              and m.active = true and c.active = true
            """)
    Optional<Member> findActivePasswordResetMemberForUpdate(@Param("memberId") long memberId,
            @Param("email") String email, @Param("companyId") long companyId);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    Page<Member> findAllByActiveTrueAndRoleIn(Set<MemberRole> role, Pageable pageable);

    long countByActiveTrueAndCompanyIdAndRole(long company, MemberRole role);
}
