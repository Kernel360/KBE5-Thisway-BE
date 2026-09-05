package org.thisway.support.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.tool.schema.spi.SchemaManagementException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.thisway.emulator.domain.Emulator;
import org.thisway.emulator.infrastructure.EmulatorRepository;
import org.thisway.vehicle.log.application.GpsLogSaveService;
import org.thisway.vehicle.log.interfaces.GpsLogRequest;
import org.thisway.vehicle.log.interfaces.GpsLogEntry;
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
    static final GenericContainer<?> RABBIT = new GenericContainer<>("rabbitmq:3.13.7-alpine")
            .withEnv("RABBITMQ_DEFAULT_USER", "test")
            .withEnv("RABBITMQ_DEFAULT_PASS", "test")
            .withExposedPorts(5672)
            .waitingFor(Wait.forLogMessage(".*Server startup complete.*", 1));

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
    @Autowired EmulatorRepository emulators;
    @Autowired GpsLogSaveService gpsSaveService;
    @Autowired ObjectMapper objectMapper;

    @ParameterizedTest(name = "DB commit before disconnect = {0}")
    @ValueSource(booleans = {false, true})
    void ack_전_연결종료로_재전달되어도_관측값은_한번만_저장된다(boolean commitBeforeDisconnect) throws Exception {
        String mdn = "redelivery-" + commitBeforeDisconnect;
        var vehicle = gpsVehicle(mdn);
        emulators.save(Emulator.builder().mdn(mdn).vehicle(vehicle).terminalId("fixture")
                .manufactureId(1).packetVersion(1).deviceId(1).deviceFirmwareVersion("1").build());
        var request = new GpsLogRequest(mdn, "fixture", "1", "1", "1", "20260905100000", "1",
                List.of(new GpsLogEntry(null, "0", "A", "37000000", "127000000", "90", "20", "100", "12")));
        var factory = new ConnectionFactory();
        factory.setHost(RABBIT.getHost());
        factory.setPort(RABBIT.getMappedPort(5672));
        factory.setUsername("test");
        factory.setPassword("test");
        factory.setAutomaticRecoveryEnabled(false);
        String queue = RabbitMQConfig.GPS_LOG_QUEUE;

        // Manual-ack harness: deliberately separates the real DB commit from broker ack.
        // This does not test Spring listener retry advice or automatic recovery.
        try (var connection = factory.newConnection(); var channel = connection.createChannel()) {
            channel.exchangeDeclare(RabbitMQConfig.GPS_LOG_EXCHANGE, "direct", true);
            channel.queueDeclare(queue, true, false, false, null);
            channel.queueBind(queue, RabbitMQConfig.GPS_LOG_EXCHANGE, RabbitMQConfig.GPS_LOG_ROUTING_KEY);
            channel.confirmSelect();
            channel.basicPublish(RabbitMQConfig.GPS_LOG_EXCHANGE, RabbitMQConfig.GPS_LOG_ROUTING_KEY,
                    null, objectMapper.writeValueAsBytes(request));
            channel.waitForConfirmsOrDie(5000);
            var first = delivery(channel, queue);
            assertThat(first.getEnvelope().isRedeliver()).isFalse();
            if (commitBeforeDisconnect) {
                gpsSaveService.saveGpsLog(objectMapper.readValue(first.getBody(), GpsLogRequest.class));
            }
            assertThat(count(mdn)).isEqualTo(commitBeforeDisconnect ? 1 : 0);
            // No basicAck: closing the channel returns its unacked delivery to the broker.
        }
        try (var connection = factory.newConnection(); var channel = connection.createChannel()) {
            var redelivered = delivery(channel, queue);
            assertThat(redelivered.getEnvelope().isRedeliver()).isTrue();
            assertThat(objectMapper.readValue(redelivered.getBody(), GpsLogRequest.class)).isEqualTo(request);
            gpsSaveService.saveGpsLog(objectMapper.readValue(redelivered.getBody(), GpsLogRequest.class));
            assertThat(count(mdn)).isEqualTo(1);
            channel.basicAck(redelivered.getEnvelope().getDeliveryTag(), false);
            channel.queueDeclarePassive(queue); // Synchronous round trip after ack on the same channel.
        }
        try (var connection = factory.newConnection(); var channel = connection.createChannel()) {
            assertThat(channel.basicGet(queue, false)).isNull();
            assertThat(count(mdn)).isEqualTo(1);
        }
    }

    private GetResponse delivery(Channel channel, String queue) {
        var received = new java.util.concurrent.atomic.AtomicReference<GetResponse>();
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(5)).until(() -> {
            received.set(channel.basicGet(queue, false));
            return received.get() != null;
        });
        return received.get();
    }

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
        assertThat(flyway.info().applied()).hasSize(3);
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

    @Test
    void 동일_관측값은_packet내_중복과_재전송에도_한번만_저장한다() {
        Vehicle vehicle = gpsVehicle("repeat");
        var first = observation(vehicle.getId(), "repeat", 20);
        logs.saveGpsLogs(List.of(first, first));
        logs.saveGpsLogs(List.of(first));
        assertThat(count("repeat")).isEqualTo(1);
        logs.saveGpsLogs(List.of(observation(vehicle.getId(), "repeat", 21)));
        assertThat(count("repeat")).isEqualTo(2); // Same timestamp, different measurement is preserved.
    }

    @Test
    void 겹치는_packet이_역순으로_동시도착해도_관측값은_각각_한번이다() throws Exception {
        Vehicle vehicle = gpsVehicle("concurrent");
        var first = observation(vehicle.getId(), "concurrent", 20);
        var second = observation(vehicle.getId(), "concurrent", 21);
        var start = new java.util.concurrent.CountDownLatch(1);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            var tasks = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 4; i++) {
                var packet = i % 2 == 0 ? List.of(first, second) : List.of(second, first);
                tasks.add(executor.submit(() -> {
                    if (!start.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                    logs.saveGpsLogs(packet);
                    return null;
                }));
            }
            start.countDown();
            for (var task : tasks) task.get(10, java.util.concurrent.TimeUnit.SECONDS);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
        assertThat(count("concurrent")).isEqualTo(2);
    }

    @Test
    void FK_실패는_무시하지_않고_packet을_rollback하여_재시도할_수_있다() {
        Vehicle vehicle = gpsVehicle("rollback");
        var valid = observation(vehicle.getId(), "rollback", 20);
        var invalid = observation(Long.MAX_VALUE, "rollback", 21);
        assertThatThrownBy(() -> logs.saveGpsLogs(List.of(valid, invalid)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(count("rollback")).isZero();
        logs.saveGpsLogs(List.of(valid));
        logs.saveGpsLogs(List.of(valid));
        assertThat(count("rollback")).isEqualTo(1);
    }

    private int count(String mdn) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM gps_log WHERE mdn=?", Integer.class, mdn);
    }

    private Vehicle gpsVehicle(String suffix) {
        var model = models.save(VehicleModel.builder().name("fixture").manufacturer("fixture").modelYear(2026).build());
        return vehicles.save(Vehicle.builder().company(company(suffix)).vehicleModel(model)
                .carNumber(suffix).color("white").mileage(0).powerOn(false).build());
    }

    private GpsLogData observation(Long vehicleId, String mdn, int speed) {
        return new GpsLogData(vehicleId, mdn, GpsStatus.NORMAL, 37.0, 127.0, 90, speed, 100, 12,
                LocalDateTime.of(2026, 9, 5, 10, 0));
    }
}
