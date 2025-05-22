package org.thisway.member.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.thisway.member.entity.Member;

public interface MemberRepository extends JpaRepository<Member, Integer> {
}
