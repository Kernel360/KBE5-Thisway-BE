package org.thisway.vehicle.repository;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.thisway.company.entity.Company;
import org.thisway.company.support.CompanyFixture;
import org.thisway.config.querydsl.QuerydslConfig;
import org.thisway.vehicle.dto.request.VehicleSearchRequest;
import org.thisway.vehicle.entity.Vehicle;
import org.thisway.vehicle.entity.VehicleModel;
import org.thisway.vehicle.support.VehicleFixture;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({VehicleQueryRepositoryImpl.class, QuerydslConfig.class})
class VehicleQueryRepositoryImplTest {

    @Autowired
    EntityManager em;

    @Autowired
    VehicleQueryRepositoryImpl vehicleQueryRepositoryImpl;

    Company company;
    VehicleModel model;

    @BeforeEach
    void setUp() {
        company = CompanyFixture.createCompany();
        model = new VehicleModel("모델A", 2025, "제조사X");

        em.persist(company);
        em.persist(model);

        em.persist(VehicleFixture.createVehicle("12가1234", company, model));
        em.persist(VehicleFixture.createVehicle("77나5678", company, model));
        em.persist(VehicleFixture.createVehicle("99다0000", company, model));
    }

    @Test
    @DisplayName("조건 없이 전체 active 차량 조회")
    void 차량번호_조건_없이_전체_조회() {
        VehicleSearchRequest request = new VehicleSearchRequest(null);
        Pageable pageable = PageRequest.of(0, 10);

        Page<Vehicle> result = vehicleQueryRepositoryImpl.searchActiveVehicles(company, request, pageable);

        assertThat(result.getContent())
                .hasSize(3)
                .extracting(Vehicle::getCarNumber)
                .containsExactlyInAnyOrder("12가1234", "77나5678", "99다0000");
    }

    @Test
    @DisplayName("차량번호로 검색 조건이 적용된다")
    void 차량번호_조건_조회() {
        VehicleSearchRequest request = new VehicleSearchRequest("12가");
        Pageable pageable = PageRequest.of(0, 10);

        Page<Vehicle> result = vehicleQueryRepositoryImpl.searchActiveVehicles(company, request, pageable);

        assertThat(result.getContent())
                .hasSize(1)
                .first()
                .extracting(Vehicle::getCarNumber)
                .isEqualTo("12가1234");
    }

    @Test
    @DisplayName("다른 회사 차량은 조회되지 않는다")
    void 다른_회사_차량_조회_제외() {
        Company otherCompany = CompanyFixture.createCompany();
        em.persist(otherCompany);
        em.persist(VehicleFixture.createVehicle("88하8888", otherCompany, model));

        VehicleSearchRequest request = new VehicleSearchRequest(null);
        Pageable pageable = PageRequest.of(0, 10);

        Page<Vehicle> result = vehicleQueryRepositoryImpl.searchActiveVehicles(company, request, pageable);

        assertThat(result.getContent())
                .extracting(Vehicle::getCarNumber)
                .doesNotContain("88하8888");
    }
}
