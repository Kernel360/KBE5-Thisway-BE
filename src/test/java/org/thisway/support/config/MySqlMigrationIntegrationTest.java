package org.thisway.support.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.tool.schema.spi.SchemaManagementException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.thisway.company.domain.Company;
import org.thisway.company.infrastructure.CompanyRepository;
import org.thisway.company.statistics.domain.Statistics;
import org.thisway.company.statistics.infrastructure.StatisticsRepository;
import org.thisway.vehicle.domain.Vehicle;
import org.thisway.vehicle.infrastructure.VehicleRepository;
import org.thisway.vehicle.vehicle_model.domain.VehicleModel;
import org.thisway.vehicle.vehicle_model.infrastructure.VehicleModelRepository;
import org.thisway.vehicle.log.domain.GeofenceLogData;
import org.thisway.vehicle.log.domain.GpsLogData;
import org.thisway.vehicle.log.domain.GpsStatus;
import org.thisway.vehicle.log.infrastructure.LogRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DirtiesContext
@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.batch.jdbc.initialize-schema=never", "spring.flyway.baseline-on-migrate=false"})
class MySqlMigrationIntegrationTest {
    @Container
    static final GenericContainer<?> MYSQL = new GenericContainer<>("mysql:8.0.40")
            .withEnv("MYSQL_DATABASE", "thisway_test")
            .withEnv("MYSQL_USER", "test")
            .withEnv("MYSQL_PASSWORD", "test")
            .withEnv("MYSQL_ROOT_PASSWORD", "test-root")
            .withExposedPorts(3306)
            .waitingFor(Wait.forLogMessage(".*ready for connections.*port: 3306.*", 1));

    static String jdbcUrl() {
        return "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306)
                + "/thisway_test?allowPublicKeyRetrieval=true&useSSL=false";
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MySqlMigrationIntegrationTest::jdbcUrl);
        registry.add("spring.datasource.username", () -> "test");
        registry.add("spring.datasource.password", () -> "test");
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @Autowired Flyway flyway;
    @Autowired JdbcTemplate jdbc;
    @Autowired CompanyRepository companies;
    @Autowired VehicleModelRepository models;
    @Autowired VehicleRepository vehicles;
    @Autowired StatisticsRepository statistics;
    @Autowired LogRepository logs;
    @Autowired JobRepository jobs;
    @Autowired EntityManagerFactory entityManagerFactory;

    @Test
    void 엔티티_컬럼과_DB가_다르면_Hibernate_validate가_실패한다() {
        jdbc.execute("ALTER TABLE statistics RENAME COLUMN power_on_count TO drifted_power_on_count");
        try {
            assertThatThrownBy(() -> entityManagerFactory.unwrap(SessionFactory.class)
                    .getSchemaManager().validateMappedObjects())
                    .isInstanceOf(SchemaManagementException.class).hasMessageContaining("power_on_count");
        } finally {
            jdbc.execute("ALTER TABLE statistics RENAME COLUMN drifted_power_on_count TO power_on_count");
        }
        entityManagerFactory.unwrap(SessionFactory.class).getSchemaManager().validateMappedObjects();
    }

    @Test
    void 빈_MySQL_migration과_JPA_validate_기동후_재실행은_noop이다() {
        assertThat(flyway.info().applied()).hasSize(2);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.statistics "
                + "WHERE table_schema=DATABASE() AND table_name='trip_log' "
                + "AND index_name='idx_trip_log_vehicle_id'", Integer.class)).isEqualTo(1);
    }

    @Test
    void 실제_Geofence_insert와_GPS_window_query가_동작한다() {
        Company company = company("logs");
        VehicleModel model = models.save(VehicleModel.builder().name("fixture").manufacturer("fixture")
                .modelYear(2026).build());
        Vehicle vehicle = vehicles.save(Vehicle.builder().company(company).vehicleModel(model)
                .carNumber("MYSQL-FIXTURE").color("white").mileage(0).powerOn(false).build());
        LocalDateTime time = LocalDateTime.of(2026, 9, 5, 10, 0);
        GpsStatus status = GpsStatus.values()[0];
        logs.saveGeofenceLog(new GeofenceLogData(vehicle.getId(), "fixture", time,
                1L, 2L, (byte) 1, status, 37.0, 127.0, 90));
        assertThat(jdbc.queryForObject("SELECT occurred_time FROM geofence_log WHERE vehicle_id=?",
                LocalDateTime.class, vehicle.getId())).isEqualTo(time);
        logs.saveGpsLogs(List.of(
                new GpsLogData(vehicle.getId(), "fixture", status, 37.0, 127.0, 90, 10, 100, 12, time),
                new GpsLogData(vehicle.getId(), "fixture", status, 38.0, 128.0, 90, 20, 200, 12, time.plusSeconds(1))));
        assertThat(logs.findCurrentGpsByVehicleIds(List.of(vehicle.getId())).get(vehicle.getId()).speed())
                .isEqualTo(20);
        assertThat(vehicles.findByIdAndCompanyIdAndActiveTrue(vehicle.getId(), company.getId())).isPresent();
        assertThat(vehicles.findByIdAndCompanyIdAndActiveTrue(vehicle.getId(), company.getId() + 1000)).isEmpty();
    }

    @Test
    void 누락됐던_Statistics를_JPA로_저장한다() {
        Statistics row = statistics.saveAndFlush(Statistics.builder().company(company("statistics"))
                .date(LocalDateTime.of(2026, 9, 5, 0, 0)).powerOnCount(1).build());
        assertThat(statistics.findById(row.getId()).orElseThrow().getPowerOnCount()).isEqualTo(1);
    }

    @Test
    void Batch_repository가_sequence와_metadata를_기록한다() throws Exception {
        var parameters = new JobParametersBuilder().addString("fixture", "migration").toJobParameters();
        var execution = jobs.createJobExecution("migration-contract", parameters);
        execution.getExecutionContext().putString("checkpoint", "saved");
        jobs.updateExecutionContext(execution);
        assertThat(jobs.getLastJobExecution("migration-contract", parameters).getExecutionContext()
                .getString("checkpoint")).isEqualTo("saved");
        var step = new org.springframework.batch.core.StepExecution("migration-step", execution);
        jobs.add(step);
        step.getExecutionContext().putLong("cursor", 42L);
        jobs.updateExecutionContext(step);
        assertThat(jobs.getLastStepExecution(execution.getJobInstance(), "migration-step")
                .getExecutionContext().getLong("cursor")).isEqualTo(42L);
    }

    @Test
    void 이력없는_비어있지_않은_DB를_자동_baseline하지_않는다() {
        Flyway untracked = Flyway.configure().dataSource(jdbcUrl(), "test", "test")
                .table("untracked_flyway_history").baselineOnMigrate(false).load();
        assertThatThrownBy(untracked::migrate).isInstanceOf(FlywayException.class)
                .hasMessageContaining("non-empty schema");
    }

    private Company company(String suffix) {
        return companies.save(Company.builder().name("fixture " + suffix).crn("fixture-" + suffix)
                .contact("000").addrRoad("fixture").addrDetail("fixture").memo("fixture")
                .gpsCycle(60).build());
    }
}
