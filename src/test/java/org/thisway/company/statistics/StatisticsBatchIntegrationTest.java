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

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers
@DirtiesContext
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
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean CompanyRepository companies;
    @MockitoSpyBean StatisticService service;

    @Test
    void 부분_실패는_전체_rollback하고_같은_날짜를_같은_instance로_재시작한다() throws Exception {
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
        assertThat(count(date)).isZero();
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
        verify(service, times(2)).saveStatistics(first, date); // Whole tasklet restarts, not company checkpointing.
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

    private int count(LocalDate date) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM statistics WHERE date=?", Integer.class, date.atStartOfDay());
    }

    private Company company(String suffix) {
        return companies.save(Company.builder().name("fixture").crn("batch-" + suffix).contact("000")
                .addrRoad("fixture").addrDetail("fixture").memo("fixture").gpsCycle(60).build());
    }
}
