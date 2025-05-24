package org.thisway.member.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.thisway.member.entity.Member;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Page<Member> findAllByActiveTrue(Pageable pageable);

    boolean existsByEmail(String email);
}
