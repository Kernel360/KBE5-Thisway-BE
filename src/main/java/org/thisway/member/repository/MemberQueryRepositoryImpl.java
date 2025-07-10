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
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.jpa.impl.JPAQuery;
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

        JPAQuery<Member> query =  queryFactory
                .selectFrom(m)
                .where(builder);

        pageable.getSort().forEach(order -> {
            Order direction = order.isAscending() ? Order.ASC : Order.DESC;
            PathBuilder<Member> path = new PathBuilder<>(Member.class, m.getMetadata());
            OrderSpecifier<String> spec = new OrderSpecifier<>(
                    direction,
                    path.get(order.getProperty(), String.class)
            );
            query.orderBy(spec);
        });

        List<Member> content = query
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
