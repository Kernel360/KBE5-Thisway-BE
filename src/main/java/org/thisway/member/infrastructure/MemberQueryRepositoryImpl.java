package org.thisway.member.infrastructure;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import org.thisway.support.common.CustomException;
import org.thisway.support.common.ErrorCode;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;
import org.thisway.member.application.CompanyChefMemberSearchCriteria;
import org.thisway.member.domain.Member;
import org.thisway.member.domain.MemberRole;
import org.thisway.member.domain.QMember;

import java.util.List;
import java.util.Set;

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

        JPAQuery<Member> query = queryFactory
                .selectFrom(m)
                .where(builder);

        if (pageable.getPageSize() > 100) throw new CustomException(ErrorCode.PAGE_INVALID_PAGE_SIZE);
        pageable.getSort().forEach(order -> {
            Order direction = order.isAscending() ? Order.ASC : Order.DESC;
            OrderSpecifier<?> spec = switch (order.getProperty()) {
                case "id" -> new OrderSpecifier<>(direction, m.id);
                case "name" -> new OrderSpecifier<>(direction, m.name);
                case "email" -> new OrderSpecifier<>(direction, m.email);
                case "role" -> new OrderSpecifier<>(direction, m.role);
                case "phone" -> new OrderSpecifier<>(direction, m.phone.value);
                case "createdAt" -> new OrderSpecifier<>(direction, m.createdAt);
                default -> throw new CustomException(ErrorCode.PAGE_INVALID_SORT_PROPERTY);
            };
            query.orderBy(spec);
        });
        if (pageable.getSort().getOrderFor("id") == null) query.orderBy(m.id.asc());

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
