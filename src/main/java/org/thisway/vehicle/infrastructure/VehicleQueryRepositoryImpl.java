package org.thisway.vehicle.infrastructure;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;
import org.thisway.company.domain.Company;
import org.thisway.vehicle.interfaces.VehicleSearchRequest;
import org.thisway.vehicle.domain.QVehicle;
import org.thisway.vehicle.domain.Vehicle;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class VehicleQueryRepositoryImpl implements VehicleQueryRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Vehicle> searchActiveVehicles(Company company, VehicleSearchRequest request, Pageable pageable) {
        QVehicle vehicle = QVehicle.vehicle;

        BooleanBuilder conditions = new BooleanBuilder();
        conditions.and(vehicle.company.eq(company));
        conditions.and(vehicle.active.isTrue());

        if (request.carNumber() != null && !request.carNumber().isBlank()) {
            conditions.and(vehicle.carNumber.containsIgnoreCase(request.carNumber()));
        }

        List<Vehicle> content = queryFactory
                .selectFrom(vehicle)
                .where(conditions)
                .orderBy(vehicle.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(vehicle.count())
                .from(vehicle)
                .where(conditions);

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    @Override
    public List<Vehicle> getAllDrivingVehicles(Long companyId) {
        QVehicle vehicle = QVehicle.vehicle;

        BooleanBuilder conditions = new BooleanBuilder();
        conditions.and(vehicle.company.id.eq(companyId));
        conditions.and(vehicle.active.isTrue());
        conditions.and(vehicle.powerOn.isTrue());

        return queryFactory
                .selectFrom(vehicle)
                .where(conditions)
                .fetch();
    }
}
