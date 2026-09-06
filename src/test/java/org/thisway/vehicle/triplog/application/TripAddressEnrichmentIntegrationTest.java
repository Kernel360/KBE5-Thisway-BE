package org.thisway.vehicle.triplog.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.thisway.company.domain.Company;
import org.thisway.company.infrastructure.CompanyRepository;
import org.thisway.vehicle.domain.Vehicle;
import org.thisway.vehicle.infrastructure.VehicleRepository;
import org.thisway.vehicle.triplog.domain.*;
import org.thisway.vehicle.vehicle_model.domain.VehicleModel;
import org.thisway.vehicle.vehicle_model.infrastructure.VehicleModelRepository;

import java.time.LocalDateTime;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@org.testcontainers.junit.jupiter.Testcontainers
@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.batch.jdbc.initialize-schema=never"})
@DirtiesContext
class TripAddressEnrichmentIntegrationTest {
    @org.testcontainers.junit.jupiter.Container
    static final org.testcontainers.containers.GenericContainer<?> MYSQL =
            new org.testcontainers.containers.GenericContainer<>("mysql:8.0.40")
                    .withEnv("MYSQL_DATABASE", "enrichment_test").withEnv("MYSQL_USER", "test")
                    .withEnv("MYSQL_PASSWORD", "test").withEnv("MYSQL_ROOT_PASSWORD", "test-root")
                    .withExposedPorts(3306).waitingFor(org.testcontainers.containers.wait.strategy.Wait
                            .forLogMessage(".*ready for connections.*port: 3306.*", 1));
    @org.springframework.test.context.DynamicPropertySource
    static void database(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:mysql://" + MYSQL.getHost() + ":"
                + MYSQL.getMappedPort(3306) + "/enrichment_test?allowPublicKeyRetrieval=true&useSSL=false");
        registry.add("spring.datasource.username", () -> "test");
        registry.add("spring.datasource.password", () -> "test");
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }
    @Autowired CompanyRepository companies;
    @Autowired VehicleModelRepository models;
    @Autowired VehicleRepository vehicles;
    @Autowired TripLogService trips;
    @Autowired TripAddressEnrichment enrichment;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean ReverseGeocodingConverter converter;

    @Test
    void 주소조회는_운행commit_이후_transaction_밖에서_실행하고_실패해도_원본은_보존한다() {
        var vehicle = vehicle();
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(count(vehicle)).isEqualTo(1); // Separate read after committed core transaction.
            throw new org.springframework.web.client.ResourceAccessException("fixture timeout");
        }).when(converter).convertToAddress(37.5, 127.0);
        assertThatCode(() -> trips.saveTripLog(input(vehicle))).doesNotThrowAnyException();
        assertThat(count(vehicle)).isEqualTo(1);
        var id = jdbc.queryForObject("SELECT id FROM trip_log WHERE vehicle_id=?", Long.class, vehicle.getId());
        assertThat(jdbc.queryForObject("SELECT on_addr FROM trip_log WHERE id=?", String.class, id)).isNull();
        doReturn(new ReverseGeocodeResult("fixture address", "detail")).when(converter).convertToAddress(37.5, 127.0);
        assertThat(enrichment.enrich(id, false)).isTrue();
        assertThat(jdbc.queryForObject("SELECT on_addr FROM trip_log WHERE id=?", String.class, id)).isEqualTo("fixture address");
        assertThat(enrichment.enrich(id, false)).isFalse();
        verify(converter, times(2)).convertToAddress(37.5, 127.0);
    }

    @Test
    void 핵심_transaction_rollback에는_외부_API를_호출하지_않는다() {
        var vehicle = vehicle();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            trips.saveTripLog(input(vehicle));
            status.setRollbackOnly();
        });
        assertThat(count(vehicle)).isZero();
        verifyNoInteractions(converter);
    }

    @Test
    void 조회중_좌표가_바뀌면_오래된_주소로_덮어쓰지_않는다() {
        var vehicle = vehicle();
        doAnswer(call -> {
            jdbc.update("UPDATE trip_log SET on_latitude=38 WHERE vehicle_id=?", vehicle.getId());
            return new ReverseGeocodeResult("stale", "stale");
        }).when(converter).convertToAddress(37.5, 127.0);
        trips.saveTripLog(input(vehicle));
        assertThat(jdbc.queryForObject("SELECT on_addr FROM trip_log WHERE vehicle_id=?", String.class, vehicle.getId())).isNull();
    }

    private int count(Vehicle vehicle) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM trip_log WHERE vehicle_id=?", Integer.class, vehicle.getId());
    }
    private TripLogSaveInput input(Vehicle vehicle) {
        return new TripLogSaveInput(vehicle,"fixture", LocalDateTime.of(2020,1,1,10,0),null,37.5,127.0,1000);
    }
    private Vehicle vehicle() {
        var company = companies.save(Company.builder().name("fixture").crn(UUID.randomUUID().toString())
                .contact("000").addrRoad("fixture").addrDetail("fixture").memo("fixture").gpsCycle(60).build());
        var model = models.save(VehicleModel.builder().name("fixture").manufacturer("fixture").modelYear(2020).build());
        return vehicles.save(Vehicle.builder().company(company).vehicleModel(model).carNumber(UUID.randomUUID().toString())
                .color("white").mileage(0).powerOn(false).build());
    }
}
