package org.thisway.company.statistics;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.thisway.company.domain.Company;
import org.thisway.company.infrastructure.CompanyRepository;
import org.thisway.company.statistics.application.*;
import org.thisway.company.statistics.domain.StatisticCalculationService;
import org.thisway.company.statistics.infrastructure.StatisticsRepository;
import org.thisway.vehicle.domain.Vehicle;
import org.thisway.vehicle.infrastructure.VehicleRepository;
import org.thisway.vehicle.triplog.domain.TripLog;
import org.thisway.vehicle.triplog.infrastructure.TripLogRepository;
import org.thisway.vehicle.vehicle_model.domain.VehicleModel;
import org.thisway.vehicle.vehicle_model.infrastructure.VehicleModelRepository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers
@DirtiesContext
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.batch.jdbc.initialize-schema=never", "thisway.statistics.cron=-",
        "thisway.statistics.correction-cron=-", "thisway.trip-address.worker.cron=-"})
class StatisticsCorrectionIntegrationTest {
    @Container
    static final GenericContainer<?> MYSQL = new GenericContainer<>("mysql:8.0.40")
            .withEnv("MYSQL_DATABASE", "correction_test").withEnv("MYSQL_USER", "test")
            .withEnv("MYSQL_PASSWORD", "test").withEnv("MYSQL_ROOT_PASSWORD", "test-root")
            .withExposedPorts(3306).waitingFor(Wait.forLogMessage(".*ready for connections.*port: 3306.*", 1));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:mysql://" + MYSQL.getHost() + ":"
                + MYSQL.getMappedPort(3306) + "/correction_test?allowPublicKeyRetrieval=true&useSSL=false");
        registry.add("spring.datasource.username", () -> "test");
        registry.add("spring.datasource.password", () -> "test");
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ApplicationEventPublisher events;
    @Autowired StatisticsCorrectionWorker worker;
    @Autowired StatisticsCorrectionScheduler scheduler;
    @Autowired StatisticsRepository statistics;
    @Autowired VehicleRepository vehicles;
    @Autowired VehicleModelRepository models;
    @Autowired TripLogRepository trips;
    @MockitoSpyBean CompanyRepository companies;
    @MockitoSpyBean StatisticService service;
    @MockitoSpyBean StatisticCalculationService calculation;
    @Autowired StatisticBatchConfig batch;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired org.thisway.company.statistics.domain.StatisticQueryService query;
    @Autowired org.thisway.vehicle.triplog.application.TripLogService tripService;
    @Autowired org.thisway.vehicle.log.application.GpsLogSaveService gpsService;
    @Autowired org.thisway.emulator.infrastructure.EmulatorRepository emulators;
    @Autowired io.micrometer.core.instrument.MeterRegistry meters;
    @MockitoSpyBean StatisticsCorrectionRequests corrections;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    org.thisway.vehicle.triplog.domain.ReverseGeocodingConverter geocoder;

    @Test
    void 완료_batch의_늦은_OFF는_영향일_통계만_같은행에서_감사와_함께_보정한다() throws Exception {
        var date = LocalDate.of(2020, 3, 1);
        var source = fixture("late", date);
        doReturn(List.of(source.companyId())).when(companies).findAllActiveCompanyIds();
        var completed = batch.runForDate(date);
        service.saveStatistics(source.companyId(), date.plusDays(1));
        long originalId = row(source.companyId(), date).getId();
        close(source, date, date.plusDays(1));
        assertThat(pending(source.companyId())).isEqualTo(2);
        assertThat(worker.process(source.companyId(), date)).isTrue();
        assertThat(worker.process(source.companyId(), date.plusDays(1))).isTrue();
        var result = row(source.companyId(), date);
        assertThat(result.getId()).isEqualTo(originalId);
        assertThat(result.getTotalDrivingTime()).isEqualTo(60);
        assertThat(result.getRevision()).isEqualTo(2);
        assertThat(row(source.companyId(), date.plusDays(1)).getUnclosedTripCount()).isZero();
        assertThat(pending(source.companyId())).isZero();
        assertThat(jdbc.queryForObject("SELECT STATUS FROM BATCH_JOB_EXECUTION WHERE JOB_EXECUTION_ID=?",
                String.class, completed.getId())).isEqualTo("COMPLETED");
        assertThat(auditCount(source.companyId(), date)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT reason FROM statistics_revision WHERE company_id=? AND revision=2 AND target_date=?",
                String.class, source.companyId(), date)).isEqualTo("LATE_TRIP_OBSERVED");
    }

    @Test
    void 중복_event는_큐_한행으로_합쳐지고_동일결과는_새_감사_revision을_만들지_않는다() {
        var date = LocalDate.of(2020, 3, 3);
        var source = fixture("duplicate", date);
        service.saveStatistics(source.companyId(), date);
        var before = row(source.companyId(), date);
        publish(source.companyId(), date, date);
        publish(source.companyId(), date, date);
        assertThat(pending(source.companyId())).isEqualTo(1);
        assertThat(worker.process(source.companyId(), date)).isTrue();
        assertThat(worker.process(source.companyId(), date)).isFalse();
        assertThat(row(source.companyId(), date).getRevision()).isEqualTo(before.getRevision());
        assertThat(row(source.companyId(), date).getCalculatedAt()).isEqualTo(before.getCalculatedAt());
        assertThat(auditCount(source.companyId(), date)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT completed_generation FROM statistics_correction_request WHERE company_id=?",
                Long.class, source.companyId())).isEqualTo(2);
    }

    @Test
    void 늦은_GPS는_발생일의_관측수만_보정하고_가동시간을_바꾸지_않는다() {
        var date = LocalDate.of(2020, 3, 12);
        var source = fixture("gps", date);
        service.saveStatistics(source.companyId(), date);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            long vehicleId = jdbc.queryForObject("SELECT vehicle_id FROM trip_log WHERE id=?", Long.class, source.tripId());
            jdbc.update("INSERT INTO gps_log(vehicle_id,mdn,occurred_time) VALUES(?,?,?)",
                    vehicleId, "fixture", date.atTime(10, 0));
            events.publishEvent(new StatisticsSourceChanged(source.companyId(), date, date, "GPS_OBSERVED"));
        });
        assertThat(worker.process(source.companyId(), date)).isTrue();
        assertThat(row(source.companyId(), date).getGpsObservationCount()).isEqualTo(1);
        assertThat(row(source.companyId(), date).getTotalDrivingTime()).isZero();
        assertThat(jdbc.queryForObject("SELECT reason FROM statistics_revision WHERE company_id=? AND revision=2",
                String.class, source.companyId())).isEqualTo("LATE_GPS_OBSERVED");
    }

    @Test
    void 원천_rollback은_dirty_marker도_같이_rollback한다() {
        var date = LocalDate.of(2020, 3, 4);
        var source = fixture("rollback", date);
        service.saveStatistics(source.companyId(), date);
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            closeInsideTransaction(source, date, date);
            throw new IllegalStateException("fixture rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(pending(source.companyId())).isZero();
        assertThat(trips.findById(source.tripId()).orElseThrow().getEndTime()).isNull();
    }

    @Test
    void 계산후_실패는_값과_감사를_rollback하고_재시도로_같은_요청을_완료한다() {
        var date = LocalDate.of(2020, 3, 5);
        var source = fixture("retry", date);
        service.saveStatistics(source.companyId(), date);
        close(source, date, date);
        doAnswer(call -> { call.callRealMethod(); throw new IllegalStateException("fixture failure"); })
                .when(service).saveStatistics(source.companyId(), date, "LATE_TRIP_OBSERVED");
        scheduler.drain();
        assertThat(row(source.companyId(), date).getTotalDrivingTime()).isZero();
        assertThat(meters.get("thisway.statistics.correction").tag("outcome", "failed").counter().count()).isPositive();
        assertThat(auditCount(source.companyId(), date)).isEqualTo(1);
        assertThat(pending(source.companyId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT attempts FROM statistics_correction_request WHERE company_id=?",
                Integer.class, source.companyId())).isEqualTo(1);
        assertThat(worker.process(source.companyId(), date)).isFalse();
        doCallRealMethod().when(service).saveStatistics(source.companyId(), date, "LATE_TRIP_OBSERVED");
        jdbc.update("UPDATE statistics_correction_request SET next_attempt_at=? WHERE company_id=?",
                date.atStartOfDay(), source.companyId());
        scheduler.drain();
        assertThat(row(source.companyId(), date).getTotalDrivingTime()).isEqualTo(60);
        assertThat(auditCount(source.companyId(), date)).isEqualTo(2);
        assertThat(pending(source.companyId())).isZero();
    }

    @Test
    void 두_worker는_같은_보정을_한번만_commit한다() throws Exception {
        var date = LocalDate.of(2020, 3, 6);
        var source = fixture("parallel", date);
        service.saveStatistics(source.companyId(), date);
        close(source, date, date);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(call -> {
            entered.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return call.callRealMethod();
        }).when(service).saveStatistics(source.companyId(), date, "LATE_TRIP_OBSERVED");
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> worker.process(source.companyId(), date));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var second = pool.submit(() -> worker.process(source.companyId(), date));
            release.countDown();
            assertThat(first.get(15, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(15, TimeUnit.SECONDS)).isFalse();
            assertThat(auditCount(source.companyId(), date)).isEqualTo(2);
        } finally { release.countDown(); pool.shutdownNow(); }
    }

    @Test
    void 첫_통계_계산과_동시도착한_event가_새_통계행을_놓치지_않는다() throws Exception {
        var date = LocalDate.of(2020, 3, 7);
        var source = fixture("initial-race", date);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var sourceStarted = new CountDownLatch(1);
        doAnswer(call -> {
            entered.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return call.callRealMethod();
        }).when(calculation).calculateDaily(source.companyId(), date.atStartOfDay());
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> service.saveStatistics(source.companyId(), date));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var second = pool.submit(() -> {
                sourceStarted.countDown();
                publish(source.companyId(), date, date);
            });
            assertThat(sourceStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> second.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
            assertThat(pending(source.companyId())).isEqualTo(1);
        } finally { release.countDown(); pool.shutdownNow(); }
    }

    @Test
    void worker_처리중_도착한_event는_완료_marker에_지워지지_않는다() throws Exception {
        var date = LocalDate.of(2020, 3, 11);
        var source = fixture("generation-race", date);
        service.saveStatistics(source.companyId(), date);
        close(source, date, date);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(call -> {
            entered.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return call.callRealMethod();
        }).when(service).saveStatistics(source.companyId(), date, "LATE_TRIP_OBSERVED");
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> worker.process(source.companyId(), date));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var second = pool.submit(() -> publish(source.companyId(), date, date));
            release.countDown();
            assertThat(first.get(15, TimeUnit.SECONDS)).isTrue();
            second.get(15, TimeUnit.SECONDS);
            assertThat(pending(source.companyId())).isEqualTo(1);
            assertThat(worker.process(source.companyId(), date)).isTrue();
            assertThat(pending(source.companyId())).isZero();
            assertThat(auditCount(source.companyId(), date)).isEqualTo(2);
        } finally { release.countDown(); pool.shutdownNow(); }
    }

    @Test
    void 과거_감사없는_row는_기존값을_revision0으로_보존한후_보정한다() {
        var date = LocalDate.of(2020, 3, 8);
        var source = fixture("legacy", date);
        service.saveStatistics(source.companyId(), date);
        long id = row(source.companyId(), date).getId();
        jdbc.update("DELETE FROM statistics_revision WHERE company_id=?", source.companyId());
        jdbc.update("UPDATE statistics SET revision=0,formula_version=1,total_driving_time=99 WHERE company_id=?",
                source.companyId());
        service.saveStatistics(source.companyId(), date);
        assertThat(row(source.companyId(), date).getId()).isEqualTo(id);
        assertThat(row(source.companyId(), date).getRevision()).isEqualTo(1);
        assertThat(auditCount(source.companyId(), date)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT snapshot_json FROM statistics_revision WHERE company_id=? AND revision=0",
                String.class, source.companyId())).contains("\"totalDrivingTime\":99", "\"formulaVersion\":1");
    }

    @Test
    void 회사격리를_지키고_미집계일과_오늘은_자동_backfill하지_않는다() {
        var date = LocalDate.of(2020, 3, 9);
        var own = fixture("own", date);
        var other = fixture("other", date);
        service.saveStatistics(own.companyId(), date);
        service.saveStatistics(other.companyId(), date);
        var today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        publish(own.companyId(), date, today.plusDays(1));
        assertThat(pending(own.companyId())).isEqualTo(1);
        assertThat(pending(other.companyId())).isZero();
        publish(other.companyId(), today, today.plusDays(1));
        assertThat(pending(other.companyId())).isZero();
        assertThat(statistics.findByCompanyIdAndDateRange(own.companyId(), date.plusDays(1), today)).isEmpty();
    }

    @Test
    void source_event는_원천_transaction_없이_발행할_수_없다() {
        var source = fixture("transaction", LocalDate.of(2020, 3, 10));
        assertThatThrownBy(() -> events.publishEvent(new StatisticsSourceChanged(source.companyId(),
                LocalDate.of(2020, 3, 10), LocalDate.of(2020, 3, 10), "GPS_OBSERVED")))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }

    @Test
    void 최초_fleet_ID를_보존하여_차량_삭제와_새차량_추가가_과거_분자분모를_바꾸지_않는다() {
        var date = LocalDate.of(2020, 3, 13);
        var source = fixture("fleet-stable", date);
        service.saveStatistics(source.companyId(), date);
        close(source, date, date);
        long oldId = jdbc.queryForObject("SELECT vehicle_id FROM trip_log WHERE id=?", Long.class, source.tripId());
        jdbc.update("UPDATE vehicle SET active=FALSE WHERE id=?", oldId);
        var model = models.save(VehicleModel.builder().name("replacement").manufacturer("fixture").modelYear(2020).build());
        var replacement = vehicles.save(Vehicle.builder().company(companies.findById(source.companyId()).orElseThrow())
                .vehicleModel(model).carNumber("replacement").color("white").mileage(0).powerOn(false).build());
        trips.save(TripLog.builder().vehicle(replacement).startTime(date.atStartOfDay()).endTime(date.plusDays(1).atStartOfDay())
                .totalTripMeter(0).active(true).build());
        jdbc.update("INSERT INTO gps_log(vehicle_id,mdn,occurred_time) VALUES(?,?,?),(?,?,?)",
                oldId, "fixture", date.atTime(10, 0), replacement.getId(), "replacement", date.atTime(10, 0));
        assertThat(worker.process(source.companyId(), date)).isTrue();
        var result = row(source.companyId(), date);
        assertThat(result.getFleetVehicleCount()).isEqualTo(1);
        assertThat(result.getTotalDrivingTime()).isEqualTo(60);
        assertThat(result.getPowerOnCount()).isEqualTo(1);
        assertThat(result.getGpsObservationCount()).isEqualTo(1);
        assertThat(query.getStatisticByDateRange(source.companyId(), date, date).quality().fleetBasis())
                .isEqualTo("INITIAL_CALCULATION_FLEET_SNAPSHOT");
    }

    @Test
    void 기존row의_알수없는_fleet은_자동추정하지_않고_ADMIN의_명시적_seed만_허용한다() throws Exception {
        var date = LocalDate.of(2020, 3, 14);
        var source = fixture("fleet-unknown", date);
        service.saveStatistics(source.companyId(), date);
        jdbc.update("DELETE FROM statistics_fleet_member WHERE company_id=?", source.companyId());
        jdbc.update("DELETE FROM statistics_fleet_snapshot WHERE company_id=?", source.companyId());
        close(source, date, date);
        assertThatThrownBy(() -> worker.process(source.companyId(), date))
                .isInstanceOf(org.thisway.support.common.CustomException.class)
                .extracting(error -> ((org.thisway.support.common.CustomException) error).getErrorCode())
                .isEqualTo(org.thisway.support.common.ErrorCode.STATISTICS_FLEET_REVIEW_REQUIRED);
        assertThat(row(source.companyId(), date).getTotalDrivingTime()).isZero();
        assertThat(pending(source.companyId())).isEqualTo(1);
        assertThat(query.getStatisticByDateRange(source.companyId(), date, date).quality().fleetBasis())
                .isEqualTo("LEGACY_FLEET_SNAPSHOT_UNKNOWN");
        var principal = org.thisway.support.security.dto.request.MemberDetails.builder()
                .username("fixture@example.test").companyId(source.companyId()).build();
        var companyAuth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal, null,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_COMPANY_ADMIN")));
        var adminAuth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal, null,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/statistics/save")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(adminAuth))
                        .param("companyId", Long.toString(source.companyId())).param("targetDate", date.toString()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/statistics/save")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(companyAuth))
                        .param("companyId", Long.toString(source.companyId())).param("targetDate", date.toString())
                        .param("captureCurrentFleet", "true"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/statistics/save")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(adminAuth))
                        .param("companyId", Long.toString(source.companyId())).param("targetDate", date.toString())
                        .param("captureCurrentFleet", "true"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        assertThat(row(source.companyId(), date).getTotalDrivingTime()).isEqualTo(60);
        assertThat(worker.process(source.companyId(), date)).isTrue();
        assertThat(pending(source.companyId())).isZero();
        assertThat(auditCount(source.companyId(), date)).isEqualTo(2);
    }

    @Test
    void 실제_Trip_저장은_queue에_연결되고_중복은_무시하며_queue실패는_운행도_rollback한다() {
        var date = LocalDate.of(2020, 3, 15);
        var source = fixture("trip-hook", date);
        var vehicle = trips.findById(source.tripId()).orElseThrow().getVehicle();
        service.saveStatistics(source.companyId(), date);
        var observed = new org.thisway.vehicle.triplog.domain.TripLogSaveInput(vehicle, "trip-hook",
                date.atTime(8, 0), date.atTime(9, 0), 37.0, 127.0, 100);
        tripService.saveTripLog(observed);
        tripService.saveTripLog(observed);
        assertThat(pending(source.companyId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT requested_generation FROM statistics_correction_request WHERE company_id=?",
                Long.class, source.companyId())).isEqualTo(1);
        assertThat(worker.process(source.companyId(), date)).isTrue();
        assertThat(row(source.companyId(), date).getTotalDrivingTime()).isEqualTo(60);

        StatisticsCorrectionRequests correctionTarget = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(corrections);
        doAnswer(call -> { call.callRealMethod(); throw new org.springframework.dao.DataIntegrityViolationException("fixture queue failure"); })
                .when(correctionTarget).sourceChanged(argThat(event -> event.companyId() == source.companyId()));
        var failed = new org.thisway.vehicle.triplog.domain.TripLogSaveInput(vehicle, "trip-hook",
                date.atTime(12, 0), date.atTime(13, 0), 37.0, 127.0, 200);
        assertThatThrownBy(() -> tripService.saveTripLog(failed))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(trips.findTop2ByVehicleIdAndStartTimeOrderByIdAsc(vehicle.getId(), date.atTime(12, 0))).isEmpty();
        assertThat(pending(source.companyId())).isZero();
        assertThat(jdbc.queryForObject("SELECT requested_generation FROM statistics_correction_request WHERE company_id=?",
                Long.class, source.companyId())).isEqualTo(1);
    }

    @Test
    void 인증된_실제_GPS_저장은_queue에_연결되고_queue실패는_GPS도_rollback한다() {
        var date = LocalDate.of(2020, 3, 16);
        var source = fixture("gps-hook", date);
        var vehicle = trips.findById(source.tripId()).orElseThrow().getVehicle();
        var emulator = emulators.save(org.thisway.emulator.domain.Emulator.builder().vehicle(vehicle).mdn("gps-hook")
                .terminalId("fixture").manufactureId(1).packetVersion(1).deviceId(1).deviceFirmwareVersion("1").build());
        var identity = new org.thisway.emulator.credential.DeviceIdentity(emulator.getId(), vehicle.getId(),
                source.companyId(), emulator.getMdn(), emulator.getAssignmentRevision());
        service.saveStatistics(source.companyId(), date);
        var request = gpsRequest("0");
        gpsService.saveGpsLog(request, identity);
        gpsService.saveGpsLog(request, identity);
        assertThat(pending(source.companyId())).isEqualTo(1);
        assertThat(worker.process(source.companyId(), date)).isTrue();
        assertThat(row(source.companyId(), date).getGpsObservationCount()).isEqualTo(1);
        assertThat(auditCount(source.companyId(), date)).isEqualTo(2);
        StatisticsCorrectionRequests correctionTarget = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(corrections);
        doAnswer(call -> { call.callRealMethod(); throw new org.springframework.dao.DataIntegrityViolationException("fixture queue failure"); })
                .when(correctionTarget).sourceChanged(argThat(event -> event.companyId() == source.companyId()));
        assertThatThrownBy(() -> gpsService.saveGpsLog(gpsRequest("1"), identity))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM gps_log WHERE vehicle_id=?", Integer.class, vehicle.getId())).isEqualTo(1);
        assertThat(pending(source.companyId())).isZero();
    }

    private org.thisway.vehicle.log.interfaces.GpsLogRequest gpsRequest(String second) {
        return new org.thisway.vehicle.log.interfaces.GpsLogRequest("gps-hook", "fixture", "1", "1", "1", "20200316100000", "1",
                List.of(new org.thisway.vehicle.log.interfaces.GpsLogEntry(null, second, "A", "37000000", "127000000",
                        "90", "20", "100", "12")));
    }

    private Fixture fixture(String suffix, LocalDate date) {
        var company = companies.save(Company.builder().name("fixture").crn("correction-" + suffix).contact("000")
                .addrRoad("fixture").addrDetail("fixture").memo("fixture").gpsCycle(60).build());
        var model = models.save(VehicleModel.builder().name("fixture").manufacturer("fixture").modelYear(2020).build());
        var vehicle = vehicles.save(Vehicle.builder().company(company).vehicleModel(model).carNumber("c-" + suffix)
                .color("white").mileage(0).powerOn(false).build());
        var trip = trips.save(TripLog.builder().vehicle(vehicle).startTime(date.atTime(10, 0)).active(false)
                .totalTripMeter(0).build());
        return new Fixture(company.getId(), trip.getId());
    }

    private void publish(long companyId, LocalDate from, LocalDate through) {
        new TransactionTemplate(transactions).executeWithoutResult(status ->
                events.publishEvent(new StatisticsSourceChanged(companyId, from, through, "TRIP_OBSERVED")));
    }

    private void close(Fixture source, LocalDate date, LocalDate through) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> closeInsideTransaction(source, date, through));
    }

    private void closeInsideTransaction(Fixture source, LocalDate date, LocalDate through) {
        jdbc.update("UPDATE trip_log SET end_time=?,active=TRUE WHERE id=?", date.atTime(11, 0), source.tripId());
        events.publishEvent(new StatisticsSourceChanged(source.companyId(), date, through, "TRIP_OBSERVED"));
    }

    private org.thisway.company.statistics.domain.Statistics row(long companyId, LocalDate date) {
        return statistics.getStatisticByCompanyIdAndDate(companyId, date.atStartOfDay(), date.plusDays(1).atStartOfDay())
                .orElseThrow();
    }

    private int pending(long companyId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM statistics_correction_request WHERE company_id=? "
                + "AND requested_generation>completed_generation", Integer.class, companyId);
    }

    private int auditCount(long companyId, LocalDate date) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM statistics_revision WHERE company_id=? AND target_date=?",
                Integer.class, companyId, date);
    }

    private record Fixture(long companyId, long tripId) { }
}
