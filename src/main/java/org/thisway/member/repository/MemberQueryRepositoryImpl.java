package org.thisway.member.repository;

import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;
import org.thisway.member.entity.Member;
import org.thisway.member.entity.MemberRole;
import org.thisway.member.entity.QMember;
import org.thisway.member.service.dto.CompanyChefMemberSearchCriteria;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class MemberQueryRepositoryImpl implements MemberQueryRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Member> searchActiveMembers(
            Set<MemberRole> role,
            Long companyId,
            CompanyChefMemberSearchCriteria criteria,
            Pageable pageable
    ) {
        QMember m = QMember.member;
        BooleanBuilder builder = new BooleanBuilder();
        builder.and(m.active.isTrue())
                .and(m.company.id.eq(companyId))
                .and(m.role.in(role));

        String memberName = criteria.memberName();
        if (StringUtils.hasText(memberName)) 
            builder.and(m.name.containsIgnoreCase(memberName.trim()));

        List<Member> content = queryFactory
            .selectFrom(m)
            .where(builder)
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();

        long total = queryFactory
            .select(m.count())
            .from(m)
            .where(builder)
            .fetchOne();

        return new PageImpl<>(content, pageable, total);
    }
}
