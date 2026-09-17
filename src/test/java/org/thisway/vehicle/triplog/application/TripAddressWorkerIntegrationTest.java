package org.thisway.vehicle.triplog.application;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.thisway.company.domain.Company;
import org.thisway.company.infrastructure.CompanyRepository;
import org.thisway.vehicle.domain.Vehicle;
import org.thisway.vehicle.infrastructure.VehicleRepository;
import org.thisway.vehicle.triplog.domain.ReverseGeocodeResult;
import org.thisway.vehicle.triplog.domain.ReverseGeocodingConverter;
import org.thisway.vehicle.vehicle_model.domain.VehicleModel;
import org.thisway.vehicle.vehicle_model.infrastructure.VehicleModelRepository;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.*;

@Testcontainers
@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.batch.jdbc.initialize-schema=never", "thisway.trip-address.worker.scan-batch-size=2",
        "thisway.trip-address.worker.max-lookups=2", "thisway.trip-address.worker.max-attempts=3"})
@DirtiesContext
class TripAddressWorkerIntegrationTest {
    @Container
    static final GenericContainer<?> MYSQL = new GenericContainer<>("mysql:8.0.40")
            .withEnv("MYSQL_DATABASE", "address_worker_test").withEnv("MYSQL_USER", "test")
            .withEnv("MYSQL_PASSWORD", "test").withEnv("MYSQL_ROOT_PASSWORD", "test-root")
            .withExposedPorts(3306).waitingFor(Wait.forLogMessage(".*ready for connections.*port: 3306.*", 1));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:mysql://" + MYSQL.getHost() + ":"
                + MYSQL.getMappedPort(3306) + "/address_worker_test?allowPublicKeyRetrieval=true&useSSL=false");
        registry.add("spring.datasource.username", () -> "test");
        registry.add("spring.datasource.password", () -> "test");
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @Autowired TripAddressWorker worker;
    @Autowired TripAddressEnrichment enrichment;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired MeterRegistry metrics;
    @Autowired CompanyRepository companies;
    @Autowired VehicleModelRepository models;
    @Autowired VehicleRepository vehicles;
    @Autowired ScheduledAnnotationBeanPostProcessor schedules;
    @MockitoBean ReverseGeocodingConverter converter;

    @BeforeEach
    void isolateTrips() {
        jdbc.update("DELETE FROM trip_log"); // Dedicated Testcontainers DB; retry rows cascade.
        jdbc.update("UPDATE trip_address_scan_state SET last_trip_id=0,high_watermark=0 WHERE id=1");
    }

    @Test
    void 기본설정은_자동_스케줄을_등록하지_않는다() {
        assertThat(schedules.getScheduledTasks()).noneMatch(task -> task.toString().contains("TripAddressWorker"));
    }

    @Test
    void commit_직후_유실된_양쪽주소를_찾고_HTTP는_transaction_밖에서_호출한다() {
        long trip = trip(37.5);
        jdbc.update("UPDATE trip_log SET off_latitude=38,off_longitude=128 WHERE id=?", trip);
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM trip_address_retry WHERE trip_id=? AND status='RUNNING'",
                    Integer.class, trip)).isEqualTo(1);
            return new ReverseGeocodeResult("fixture", "detail");
        }).when(converter).convertToAddress(anyDouble(), anyDouble());

        var result = worker.runOnce();

        assertThat(result.discovered()).isEqualTo(2);
        assertThat(result.claimed()).isEqualTo(2);
        assertThat(result.updated()).isEqualTo(2);
        assertThat(address(trip)).isEqualTo("fixture");
        assertThat(jdbc.queryForObject("SELECT off_addr FROM trip_log WHERE id=?", String.class, trip)).isEqualTo("fixture");
        assertThat(retries()).isZero();
        assertThat(worker.runOnce().claimed()).isZero();
        verify(converter, times(2)).convertToAddress(anyDouble(), anyDouble());
    }

    @Test
    void 실패의_백오프와_시도횟수는_worker_재생성에도_남고_한도뒤에는_명시적으로_재개한다() {
        long trip = trip(37.5);
        when(converter.convertToAddress(anyDouble(), anyDouble())).thenThrow(new IllegalStateException("fixture only"));
        assertThat(worker.runOnce().deferred()).isEqualTo(1);
        assertThat(retry(trip, "attempts", Integer.class)).isEqualTo(1);
        assertBackoff(trip, 60);
        assertThat(worker.runOnce().claimed()).isZero();

        var restarted = freshWorker();
        due(trip);
        assertThat(restarted.runOnce().deferred()).isEqualTo(1);
        assertThat(retry(trip, "attempts", Integer.class)).isEqualTo(2);
        assertBackoff(trip, 120);
        due(trip);
        assertThat(restarted.runOnce().exhausted()).isEqualTo(1);
        assertThat(retry(trip, "status", String.class)).isEqualTo("EXHAUSTED");
        due(trip);
        assertThat(freshWorker().runOnce().claimed()).isZero();
        verify(converter, times(3)).convertToAddress(anyDouble(), anyDouble());

        // Runbook operation after the operator fixes the cause; only an exhausted row is reset.
        jdbc.update("UPDATE trip_address_retry SET status='PENDING',attempts=0,next_attempt_at=UTC_TIMESTAMP(6) "
                + "WHERE trip_id=? AND side='on' AND status='EXHAUSTED'", trip);
        doReturn(new ReverseGeocodeResult("recovered", "")).when(converter).convertToAddress(anyDouble(), anyDouble());
        assertThat(restarted.runOnce().updated()).isEqualTo(1);
        assertThat(address(trip)).isEqualTo("recovered");
    }

    @Test
    void 영구실패와_연속신규운행에도_저장된_high_watermark_cursor는_뒤의_운행까지_진행한다() {
        long failed = trip(1);
        long second = trip(2);
        long third = trip(3);
        when(converter.convertToAddress(anyDouble(), anyDouble())).thenReturn(new ReverseGeocodeResult("fixture", ""));
        when(converter.convertToAddress(1, 127)).thenThrow(new IllegalStateException("fixture only"));

        assertThat(worker.runOnce().claimed()).isEqualTo(2);
        long newest = trip(4);
        assertThat(freshWorker().runOnce().updated()).isEqualTo(1);
        assertThat(address(third)).isEqualTo("fixture");
        assertThat(address(second)).isEqualTo("fixture");
        assertThat(address(failed)).isNull();
        assertThat(jdbc.queryForObject("SELECT high_watermark FROM trip_address_scan_state WHERE id=1", Long.class))
                .isEqualTo(third); // New inserts cannot extend an in-progress sweep forever.
        for (int i = 0; i < 3; i++) worker.runOnce();
        assertThat(address(newest)).isEqualTo("fixture");
        verify(converter, times(1)).convertToAddress(1, 127);
    }

    @Test
    void 과거운행의_나중에_도착한_OFF_좌표도_다음_순환에서_찾는다() {
        long old = trip(37.5);
        when(converter.convertToAddress(anyDouble(), anyDouble())).thenReturn(new ReverseGeocodeResult("fixture", ""));
        worker.runOnce();
        jdbc.update("UPDATE trip_log SET off_latitude=38,off_longitude=128 WHERE id=?", old);
        assertThat(worker.runOnce().updated()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT off_addr FROM trip_log WHERE id=?", String.class, old)).isEqualTo("fixture");
    }

    @Test
    void 유효한_lease가_있는_동안_다른_worker는_같은_주소를_호출하지_않는다() throws Exception {
        long trip = trip(37.5);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(call -> {
            entered.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout");
            return new ReverseGeocodeResult("fixture", "");
        }).when(converter).convertToAddress(anyDouble(), anyDouble());
        try (var executor = Executors.newSingleThreadExecutor()) {
            var first = executor.submit(worker::runOnce);
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(freshWorker().runOnce().claimed()).isZero();
            } finally {
                release.countDown();
            }
            assertThat(first.get(10, TimeUnit.SECONDS).updated()).isEqualTo(1);
        }
        assertThat(address(trip)).isEqualTo("fixture");
        verify(converter, times(1)).convertToAddress(anyDouble(), anyDouble());
    }

    @Test
    void lease만료_후_다른_worker가_복구하면_이전_claim의_완료는_새상태를_덮지_않는다() throws Exception {
        long trip = trip(37.5);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        doAnswer(call -> {
            if (calls.incrementAndGet() == 1) {
                entered.countDown();
                if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout");
                return new ReverseGeocodeResult("old response", "");
            }
            return new ReverseGeocodeResult("recovered", "");
        }).when(converter).convertToAddress(anyDouble(), anyDouble());
        try (var executor = Executors.newSingleThreadExecutor()) {
            var first = executor.submit(worker::runOnce);
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                jdbc.update("UPDATE trip_address_retry SET lease_until=TIMESTAMPADD(SECOND,-1,UTC_TIMESTAMP(6)) WHERE trip_id=?", trip);
                assertThat(freshWorker().runOnce().updated()).isEqualTo(1);
            } finally {
                release.countDown();
            }
            first.get(10, TimeUnit.SECONDS);
        }
        assertThat(address(trip)).isEqualTo("recovered");
        assertThat(retries()).isZero();
        verify(converter, times(2)).convertToAddress(anyDouble(), anyDouble());
    }

    @Test
    void 최종시도_중_crash로_만료된_claim은_한도를_넘겨_호출하지_않고_점검대상으로_남는다() {
        long trip = trip(37.5);
        jdbc.update("""
                INSERT INTO trip_address_retry(trip_id,side,status,attempts,claim_token,lease_until)
                VALUES(?,'on','RUNNING',3,?,TIMESTAMPADD(SECOND,-1,UTC_TIMESTAMP(6)))
                """, trip, UUID.randomUUID().toString());
        assertThat(worker.runOnce().exhausted()).isEqualTo(1);
        assertThat(retry(trip, "status", String.class)).isEqualTo("EXHAUSTED");
        assertThat(address(trip)).isNull();
        verifyNoInteractions(converter);
    }

    @Test
    void 다른경로가_이미_주소를_채웠으면_외부재조회_없이_대기상태를_정리한다() {
        long trip = trip(37.5);
        jdbc.update("INSERT INTO trip_address_retry(trip_id,side) VALUES(?,'on')", trip);
        jdbc.update("UPDATE trip_log SET on_addr='existing' WHERE id=?", trip);
        assertThat(worker.runOnce().noLongerNeeded()).isEqualTo(1);
        assertThat(retries()).isZero();
        assertThat(address(trip)).isEqualTo("existing");
        verifyNoInteractions(converter);
    }

    @Test
    void HTTP_중_좌표가_바뀌면_이전주소를_버리고_백오프후_새좌표로_복구한다() {
        long trip = trip(37.5);
        doAnswer(call -> {
            jdbc.update("UPDATE trip_log SET on_latitude=38 WHERE id=?", trip);
            return new ReverseGeocodeResult("stale", "");
        }).when(converter).convertToAddress(37.5, 127);
        assertThat(worker.runOnce().deferred()).isEqualTo(1);
        assertThat(address(trip)).isNull();
        due(trip);
        doReturn(new ReverseGeocodeResult("current", "")).when(converter).convertToAddress(38, 127);
        assertThat(worker.runOnce().updated()).isEqualTo(1);
        assertThat(address(trip)).isEqualTo("current");
    }

    @Test
    void 호출자_transaction이_있어도_HTTP에는_전파하지_않는다() {
        long trip = trip(37.5);
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new ReverseGeocodeResult("committed", "");
        }).when(converter).convertToAddress(anyDouble(), anyDouble());
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            assertThat(worker.runOnce().updated()).isEqualTo(1);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            status.setRollbackOnly();
        });
        assertThat(address(trip)).isEqualTo("committed");
    }

    private TripAddressWorker freshWorker() {
        return new TripAddressWorker(jdbc, enrichment, transactions, metrics, 2, 2, 3, 60, 3600, 30);
    }

    private void assertBackoff(long trip, int seconds) {
        assertThat(jdbc.queryForObject("SELECT TIMESTAMPDIFF(SECOND,UTC_TIMESTAMP(6),next_attempt_at) "
                + "FROM trip_address_retry WHERE trip_id=? AND side='on'", Integer.class, trip))
                .isBetween(seconds - 5, seconds);
    }

    private void due(long trip) {
        jdbc.update("UPDATE trip_address_retry SET next_attempt_at=TIMESTAMPADD(SECOND,-1,UTC_TIMESTAMP(6)) WHERE trip_id=?", trip);
    }

    private <T> T retry(long trip, String column, Class<T> type) {
        return jdbc.queryForObject("SELECT " + column + " FROM trip_address_retry WHERE trip_id=? AND side='on'", type, trip);
    }

    private int retries() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM trip_address_retry", Integer.class);
    }

    private String address(long trip) {
        return jdbc.queryForObject("SELECT on_addr FROM trip_log WHERE id=?", String.class, trip);
    }

    private long trip(double latitude) {
        var company = companies.save(Company.builder().name("fixture").crn(UUID.randomUUID().toString())
                .contact("000").addrRoad("fixture").addrDetail("fixture").memo("fixture").gpsCycle(60).build());
        var model = models.save(VehicleModel.builder().name("fixture").manufacturer("fixture").modelYear(2020).build());
        var vehicle = vehicles.save(Vehicle.builder().company(company).vehicleModel(model).carNumber(UUID.randomUUID().toString())
                .color("white").mileage(0).powerOn(false).build());
        jdbc.update("""
                INSERT INTO trip_log(vehicle_id,active,created_at,start_time,total_trip_meter,on_latitude,on_longitude)
                VALUES(?,0,UTC_TIMESTAMP(6),'2020-01-01 10:00:00',1000,?,127)
                """, vehicle.getId(), latitude);
        return jdbc.queryForObject("SELECT id FROM trip_log WHERE vehicle_id=?", Long.class, vehicle.getId());
    }
}
