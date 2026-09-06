package org.thisway.company.statistics;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.thisway.company.domain.Company;
import org.thisway.company.infrastructure.CompanyRepository;
import org.thisway.company.statistics.application.StatisticService;
import org.thisway.company.statistics.application.StatisticsCompanyWorker;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers
@DirtiesContext
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.batch.jdbc.initialize-schema=never", "thisway.statistics.cron=-"})
class StatisticsBatchIntegrationTest {
    @Container
    static final GenericContainer<?> MYSQL = new GenericContainer<>("mysql:8.0.40")
            .withEnv("MYSQL_DATABASE", "statistics_test").withEnv("MYSQL_USER", "test")
            .withEnv("MYSQL_PASSWORD", "test").withEnv("MYSQL_ROOT_PASSWORD", "test-root")
            .withExposedPorts(3306).waitingFor(Wait.forLogMessage(".*ready for connections.*port: 3306.*", 1));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:mysql://" + MYSQL.getHost() + ":"
                + MYSQL.getMappedPort(3306) + "/statistics_test?allowPublicKeyRetrieval=true&useSSL=false");
        registry.add("spring.datasource.username", () -> "test");
        registry.add("spring.datasource.password", () -> "test");
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @Autowired StatisticBatchConfig batch;
    @Autowired JobLauncher launcher;
    @Autowired Job statisticsJob;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired StatisticsCompanyWorker worker;
    @Autowired org.thisway.vehicle.infrastructure.VehicleRepository vehicles;
    @Autowired org.thisway.vehicle.vehicle_model.infrastructure.VehicleModelRepository models;
    @Autowired org.thisway.vehicle.triplog.infrastructure.TripLogRepository trips;
    @Autowired org.thisway.company.statistics.infrastructure.StatisticsRepository statistics;
    @Autowired org.thisway.company.statistics.domain.StatisticQueryService query;
    @Autowired org.springframework.batch.core.repository.JobRepository jobs;
    @MockitoSpyBean CompanyRepository companies;
    @MockitoSpyBean StatisticService service;

    @Test
    void 부분_실패는_성공회사를_보존하고_같은_instance에서_실패회사만_재시작한다() throws Exception {
        LocalDate date = LocalDate.of(2020, 1, 2);
        Long first = company("first").getId();
        Long second = company("second").getId();
        doReturn(List.of(first, second)).when(companies).findAllActiveCompanyIds();
        var fail = new AtomicBoolean(true);
        doAnswer(invocation -> {
            if (fail.get()) throw new IllegalStateException("fixture failure");
            return invocation.callRealMethod();
        }).when(service).saveStatistics(second, date);

        var failed = batch.runForDate(date);
        assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(failed.getStepExecutions()).singleElement().satisfies(step -> {
            assertThat(step.getStatus()).isEqualTo(BatchStatus.FAILED);
            assertThat(step.getExitStatus().getExitDescription()).contains("companyId=" + second, "targetDate=" + date);
        });
        assertThat(count(date)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM statistics_checkpoint WHERE job_instance_id=?",
                Integer.class, failed.getJobInstance().getInstanceId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT STATUS FROM BATCH_JOB_EXECUTION WHERE JOB_EXECUTION_ID=?",
                String.class, failed.getId())).isEqualTo("FAILED");
        verify(service).saveStatistics(first, date);

        fail.set(false);
        var restarted = batch.runForDate(date);
        assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(restarted.getJobInstance().getInstanceId()).isEqualTo(failed.getJobInstance().getInstanceId());
        assertThat(restarted.getId()).isNotEqualTo(failed.getId());
        assertThat(count(date)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT SUM(power_on_count) FROM statistics WHERE date=?",
                Integer.class, date.atStartOfDay())).isZero();
        verify(service, times(1)).saveStatistics(first, date);
        assertThatThrownBy(() -> batch.runForDate(date)).isInstanceOf(JobInstanceAlreadyCompleteException.class);
        assertThat(count(date)).isEqualTo(2);
    }

    @Test
    void 같은_날짜의_진행중인_실행은_중복_시작할_수_없다() throws Exception {
        LocalDate date = LocalDate.of(2020, 1, 3);
        Long companyId = company("concurrent").getId();
        doReturn(List.of(companyId)).when(companies).findAllActiveCompanyIds();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout");
            return invocation.callRealMethod();
        }).when(service).saveStatistics(companyId, date);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var running = executor.submit(() -> batch.runForDate(date));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            try {
                assertThatThrownBy(() -> batch.runForDate(date)).isInstanceOf(JobExecutionAlreadyRunningException.class);
            } finally {
                release.countDown();
            }
            assertThat(running.get(10, TimeUnit.SECONDS).getStatus()).isEqualTo(BatchStatus.COMPLETED);
            assertThat(count(date)).isEqualTo(1);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void 날짜_누락_오류_비식별_추가파라미터는_업무실행전에_거부한다() {
        var invalid = List.of(new JobParameters(),
                new JobParametersBuilder().addString("targetDate", "2026-02-30").toJobParameters(),
                new JobParametersBuilder().addString("targetDate", "2020-01-01", false).toJobParameters(),
                new JobParametersBuilder().addString("targetDate", "2020-01-01")
                        .addLong("timestamp", 1L).toJobParameters());
        for (var parameters : invalid) {
            assertThatThrownBy(() -> launcher.run(statisticsJob, parameters))
                    .isInstanceOf(JobParametersInvalidException.class);
        }
        verify(companies, never()).findAllActiveCompanyIds();
    }

    @Test
    void checkpoint_저장이_실패하면_회사통계도_rollback한다() {
        var date = LocalDate.of(2020, 1, 4);
        long id = company("checkpoint-failure").getId();
        // Invalid JobInstance FK fails after real calculation and statistics save.
        assertThatThrownBy(() -> worker.process(-1, id, date))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(count(date)).isZero();
    }

    @Test
    void 직접_저장_동시요청도_회사별로_직렬화하고_달력날짜_unique를_지킨다() throws Exception {
        var date = LocalDate.of(2020, 1, 5);
        long id = company("direct-race").getId();
        var executor = Executors.newFixedThreadPool(4);
        var ready = new CountDownLatch(4);
        var start = new CountDownLatch(1);
        try {
            var tasks = new java.util.ArrayList<Future<?>>();
            for (int i = 0; i < 4; i++) tasks.add(executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                service.saveStatistics(id, date);
                return null;
            }));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (var task : tasks) task.get(15, TimeUnit.SECONDS);
            assertThat(count(date)).isEqualTo(1);
            // Even a bypass writer using a non-midnight timestamp cannot create a second daily row.
            jdbc.update("UPDATE statistics SET date=? WHERE company_id=?", date.atTime(12, 0), id);
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO statistics(active,created_at,company_id,date,power_on_count,
                    hour00,hour01,hour02,hour03,hour04,hour05,hour06,hour07,hour08,hour09,hour10,hour11,
                    hour12,hour13,hour14,hour15,hour16,hour17,hour18,hour19,hour20,hour21,hour22,hour23)
                    VALUES(1,NOW(),?,?,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0)
                    """, id, date.atStartOfDay()))
                    .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void 완료운행_일자분할_중복합집합과_GPS관측은_독립적이고_tenant가_격리된다() {
        var date = LocalDate.of(2020, 2, 1);
        var company = company("formula");
        var a = vehicle(company, "a");
        var b = vehicle(company, "b");
        var other = vehicle(company("other-formula"), "other");
        var start = date.atStartOfDay();
        trip(a, start.minusMinutes(30), start.plusMinutes(30));
        trip(a, start.plusHours(9), start.plusHours(11));
        trip(a, start.plusHours(9), start.plusHours(11));
        trip(a, start.plusHours(10), start.plusHours(12));
        trip(a, start.plusHours(23).plusMinutes(30), start.plusDays(1).plusMinutes(30));
        trip(b, start.plusHours(9).plusMinutes(30), start.plusHours(10).plusMinutes(30));
        trip(a, start.plusHours(13), null);
        trip(a, start.plusDays(1), start.plusDays(1).plusHours(1));
        trip(other, start, start.plusDays(1));
        service.saveStatistics(company.getId(), date);
        var beforeGps = query.getStatisticByDateRange(company.getId(), date, date);
        assertThat(beforeGps.totalDrivingTime()).isEqualTo(300);
        assertThat(beforeGps.powerOnCount()).isEqualTo(5);
        assertThat(beforeGps.hours().get(0)).isEqualTo(25);
        assertThat(beforeGps.hours().get(9)).isEqualTo(75);
        assertThat(beforeGps.hours().get(10)).isEqualTo(75);
        assertThat(beforeGps.hours().get(11)).isEqualTo(50);
        assertThat(beforeGps.hours().get(23)).isEqualTo(25);
        assertThat(beforeGps.averageOperationRate()).isCloseTo(300.0 / (2 * 1440) * 100, within(1e-9));
        assertThat(beforeGps.quality().unclosedTripDays()).isEqualTo(1);
        assertThat(beforeGps.quality().gpsObservationCount()).isZero();
        jdbc.update("INSERT INTO gps_log(vehicle_id,mdn,occurred_time) VALUES(?,?,?),(?,?,?),(?,?,?)",
                a.getId(), "fixture", start.plusHours(9),
                a.getId(), "fixture", start.plusDays(1), other.getId(), "fixture", start.plusHours(9));
        jdbc.update("UPDATE company SET gps_cycle=5 WHERE id=?", company.getId());
        service.saveStatistics(company.getId(), date);
        var afterGps = query.getStatisticByDateRange(company.getId(), date, date);
        assertThat(afterGps.hours()).isEqualTo(beforeGps.hours());
        assertThat(afterGps.quality().gpsObservationCount()).isEqualTo(1);
        assertThat(count(date)).isEqualTo(1);
    }

    @Test
    void 늦은_OFF는_명시적_재계산으로_같은행을_보정하고_완료_job을_속여_재실행하지_않는다() throws Exception {
        var date = LocalDate.of(2020, 2, 2);
        var company = company("late-off");
        var vehicle = vehicle(company, "late");
        var trip = trip(vehicle, date.atTime(9, 0), null);
        doReturn(List.of(company.getId())).when(companies).findAllActiveCompanyIds();
        assertThat(batch.runForDate(date).getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(query.getStatisticByDateRange(company.getId(), date, date).totalDrivingTime()).isZero();
        jdbc.update("UPDATE trip_log SET end_time=?,active=1 WHERE id=?", date.atTime(11, 0), trip.getId());
        assertThatThrownBy(() -> batch.runForDate(date)).isInstanceOf(JobInstanceAlreadyCompleteException.class);
        service.saveStatistics(company.getId(), date); // Existing administrator correction use case.
        var corrected = query.getStatisticByDateRange(company.getId(), date, date);
        assertThat(corrected.totalDrivingTime()).isEqualTo(120);
        assertThat(corrected.quality().unclosedTripDays()).isZero();
        assertThat(count(date)).isEqualTo(1);
    }

    @Test
    void 이전공식과_미집계일은_0일로_섞지_않고_coverage를_노출한다() {
        var date = LocalDate.of(2020, 2, 3);
        var company = company("coverage");
        statistics.save(org.thisway.company.statistics.domain.Statistics.builder().company(company)
                .date(date.atStartOfDay()).powerOnCount(99).totalDrivingTime(999).averageOperationRate(99.0).build());
        service.saveStatistics(company.getId(), date.plusDays(1));
        var result = query.getStatisticByDateRange(company.getId(), date, date.plusDays(2));
        assertThat(result.powerOnCount()).isZero();
        assertThat(result.totalDrivingTime()).isZero();
        assertThat(result.quality().coveredDays()).isEqualTo(1);
        assertThat(result.quality().requestedDays()).isEqualTo(3);
        assertThat(result.quality().excludedLegacyDays()).isEqualTo(1);
        assertThat(query.getStatisticByDateRange(company.getId(), date, date).quality().coveredDays()).isZero();
        assertThatThrownBy(() -> query.getStatisticByDateRange(company.getId(), date.plusDays(1), date))
                .isInstanceOf(org.thisway.support.common.CustomException.class);
        assertThatThrownBy(() -> query.getStatisticByDateRange(company.getId(), date, date.plusDays(366)))
                .isInstanceOf(org.thisway.support.common.CustomException.class);
        assertThatThrownBy(() -> service.saveStatistics(company.getId(), LocalDate.now(java.time.ZoneId.of("Asia/Seoul"))))
                .isInstanceOf(org.thisway.support.common.CustomException.class);
    }

    @Test
    void 이전공식_checkpoint는_성공으로_건너뛰지_않고_같은키를_갱신한다() throws Exception {
        var date = LocalDate.of(2020, 2, 6);
        var id = company("version-marker").getId();
        var job = jobs.createJobExecution("formula-fixture", new JobParametersBuilder().addString("date", date.toString()).toJobParameters());
        long instance = job.getJobInstance().getInstanceId();
        worker.process(instance, id, date);
        jdbc.update("UPDATE statistics SET formula_version=1 WHERE company_id=?", id);
        jdbc.update("UPDATE statistics_checkpoint SET formula_version=1 WHERE company_id=?", id);
        worker.process(instance, id, date);
        verify(service, times(2)).saveStatistics(id, date);
        assertThat(jdbc.queryForObject("SELECT formula_version FROM statistics_checkpoint WHERE company_id=?", Integer.class, id)).isEqualTo(2);
        assertThat(query.getStatisticByDateRange(id, date, date).quality().coveredDays()).isEqualTo(1);
    }

    @Test
    void 통계_HTTP는_인증회사만_조회하고_quality를_직렬화하며_잘못된_기간은_400이다() throws Exception {
        var date = LocalDate.of(2020, 2, 7);
        var own = company("http-own");
        var other = company("http-other");
        service.saveStatistics(own.getId(), date);
        var principal = org.thisway.support.security.dto.request.MemberDetails.builder()
                .username("fixture@example.test").companyId(own.getId()).build();
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal, null,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_COMPANY_ADMIN")));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/statistics")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(auth))
                        .param("companyId", other.getId().toString()).param("startDate", date.toString()).param("endDate", date.toString()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.companyId").value(own.getId()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.quality.formulaVersion").value(2))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.quality.coveredDays").value(1));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/statistics")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(auth))
                        .param("startDate", date.plusDays(1).toString()).param("endDate", date.toString()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
    }

    private org.thisway.vehicle.domain.Vehicle vehicle(Company company, String suffix) {
        var model = models.save(org.thisway.vehicle.vehicle_model.domain.VehicleModel.builder()
                .name("fixture").manufacturer("fixture").modelYear(2020).build());
        return vehicles.save(org.thisway.vehicle.domain.Vehicle.builder().company(company).vehicleModel(model)
                .carNumber("formula-" + suffix).color("white").mileage(0).powerOn(false).build());
    }

    private org.thisway.vehicle.triplog.domain.TripLog trip(org.thisway.vehicle.domain.Vehicle vehicle,
            java.time.LocalDateTime start, java.time.LocalDateTime end) {
        return trips.save(org.thisway.vehicle.triplog.domain.TripLog.builder().vehicle(vehicle)
                .startTime(start).endTime(end).totalTripMeter(0).active(end != null).build());
    }

    private int count(LocalDate date) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM statistics WHERE date=?", Integer.class, date.atStartOfDay());
    }

    private Company company(String suffix) {
        return companies.save(Company.builder().name("fixture").crn("batch-" + suffix).contact("000")
                .addrRoad("fixture").addrDetail("fixture").memo("fixture").gpsCycle(60).build());
    }
}
