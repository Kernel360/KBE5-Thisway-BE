package org.thisway.support.batch;

import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.thisway.ThiswayApplication;
import org.thisway.company.domain.Company;
import org.thisway.company.infrastructure.CompanyRepository;
import org.thisway.company.statistics.StatisticBatchConfig;
import org.thisway.company.statistics.application.StatisticsCompanyWorker;
import org.thisway.ops.StatisticsOrphanRecovery;
import org.thisway.vehicle.domain.Vehicle;
import org.thisway.vehicle.infrastructure.VehicleRepository;
import org.thisway.vehicle.vehicle_model.domain.VehicleModel;
import org.thisway.vehicle.vehicle_model.infrastructure.VehicleModelRepository;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.doReturn;

@Tag("statistics-crash")
@Testcontainers
@DirtiesContext
@SpringBootTest(classes = ThiswayApplication.class, properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.batch.jdbc.initialize-schema=never", "thisway.statistics.cron=-",
        "thisway.statistics.correction-cron=-", "thisway.trip-address.worker.cron=-"})
class StatisticsCrashRecoveryIntegrationTest {
    @Container
    static final GenericContainer<?> MYSQL = new GenericContainer<>("mysql:8.0.40")
            .withEnv("MYSQL_DATABASE", "statistics_crash_test").withEnv("MYSQL_USER", "test")
            .withEnv("MYSQL_PASSWORD", "test").withEnv("MYSQL_ROOT_PASSWORD", "test-root")
            .withExposedPorts(3306).waitingFor(Wait.forLogMessage(".*ready for connections.*port: 3306.*", 1));

    static String jdbcUrl() {
        return "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306)
                + "/statistics_crash_test?allowPublicKeyRetrieval=true&useSSL=false";
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", StatisticsCrashRecoveryIntegrationTest::jdbcUrl);
        registry.add("spring.datasource.username", () -> "test");
        registry.add("spring.datasource.password", () -> "test");
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    @Autowired StatisticBatchConfig batch;
    @Autowired JobRepository jobs;
    @Autowired VehicleRepository vehicles;
    @Autowired VehicleModelRepository models;
    @MockitoSpyBean CompanyRepository companies;

    @Test
    void 실제_별도JVM을_강제종료한_뒤_정확한_orphan만_복구하고_같은_instance_checkpoint에서_재시작한다() throws Exception {
        LocalDate date = LocalDate.of(2020, 3, 1);
        Company first = company("first");
        Company second = company("second");
        long firstVehicle = vehicle(first);
        long secondVehicle = vehicle(second);
        trip(firstVehicle, date.atTime(9, 0), date.atTime(10, 0));
        trip(secondVehicle, date.atTime(9, 0), date.atTime(11, 0));
        doReturn(List.of(first.getId(), second.getId())).when(companies).findAllActiveCompanyIds();

        long executionId;
        long instanceId;
        long childPid;
        try (var child = new CrashProcess(date, first.getId(), second.getId())) {
            child.awaitCheckpoint();
            childPid = child.process.pid();
            assertThat(child.process.isAlive()).isTrue();
            executionId = jdbc.queryForObject("""
                    SELECT e.JOB_EXECUTION_ID FROM BATCH_JOB_EXECUTION e JOIN BATCH_JOB_EXECUTION_PARAMS p
                    ON p.JOB_EXECUTION_ID=e.JOB_EXECUTION_ID WHERE p.PARAMETER_NAME='targetDate' AND p.PARAMETER_VALUE=?
                    """, Long.class, date.toString());
            instanceId = jdbc.queryForObject("SELECT JOB_INSTANCE_ID FROM BATCH_JOB_EXECUTION WHERE JOB_EXECUTION_ID=?",
                    Long.class, executionId);
            assertThat(status(executionId)).isEqualTo("STARTED");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM statistics_checkpoint WHERE job_instance_id=?",
                    Integer.class, instanceId)).isEqualTo(1);
            assertThatThrownBy(() -> batch.runForDate(date)).isInstanceOf(JobExecutionAlreadyRunningException.class);
            assertThatThrownBy(() -> new StatisticsOrphanRecovery.OfflineConfirmation("fixture", "writer-still-running", false))
                    .isInstanceOf(IllegalArgumentException.class);

            child.process.destroyForcibly();
            assertThat(child.process.waitFor(10, TimeUnit.SECONDS)).isTrue();
            assertThat(child.process.isAlive()).isFalse();
            assertThat(child.process.exitValue()).isNotZero();
        }

        assertThat(status(executionId)).isEqualTo("STARTED"); // SIGKILL cannot write FAILED metadata.
        var firstBefore = jdbc.queryForMap("SELECT * FROM statistics WHERE company_id=? AND statistic_day=?", first.getId(), date);
        var checkpointBefore = jdbc.queryForMap("SELECT * FROM statistics_checkpoint WHERE job_instance_id=? AND company_id=?",
                instanceId, first.getId());
        assertThat(((Number) firstBefore.get("total_driving_time")).intValue()).isEqualTo(60);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM statistics WHERE company_id=? AND statistic_day=?",
                Integer.class, second.getId(), date)).isZero();
        trip(firstVehicle, date.atTime(12, 0), date.atTime(13, 0)); // A restart must still honor the committed checkpoint.

        var snapshot = preview(executionId);
        recover(snapshot, "local-pid-" + childPid + "-terminated");
        assertThat(status(executionId)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT STATUS FROM BATCH_STEP_EXECUTION WHERE JOB_EXECUTION_ID=?",
                String.class, executionId)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM statistics_orphan_recovery_audit WHERE job_execution_id=?",
                Integer.class, executionId)).isEqualTo(1);

        var restarted = batch.runForDate(date);
        assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(restarted.getId()).isNotEqualTo(executionId);
        assertThat(restarted.getJobInstance().getInstanceId()).isEqualTo(instanceId);
        assertThat(jdbc.queryForMap("SELECT * FROM statistics WHERE company_id=? AND statistic_day=?", first.getId(), date))
                .isEqualTo(firstBefore);
        assertThat(jdbc.queryForMap("SELECT * FROM statistics_checkpoint WHERE job_instance_id=? AND company_id=?",
                instanceId, first.getId())).isEqualTo(checkpointBefore);
        assertThat(jdbc.queryForObject("SELECT total_driving_time FROM statistics WHERE company_id=? AND statistic_day=?",
                Integer.class, second.getId(), date)).isEqualTo(120);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM statistics_checkpoint WHERE job_instance_id=?",
                Integer.class, instanceId)).isEqualTo(2);
        assertThatThrownBy(() -> recover(snapshot, "stale-confirmation")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> recover(preview(restarted.getId()), "completed-job"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(status(restarted.getId())).isEqualTo("COMPLETED");
    }

    @Test
    void preview뒤_job_version이_바뀌면_이전승인으로_덮어쓰지_않는다() throws Exception {
        JobExecution job = orphan(LocalDate.of(2020, 3, 2));
        var snapshot = preview(job.getId());
        jdbc.update("UPDATE BATCH_JOB_EXECUTION SET VERSION=VERSION+1 WHERE JOB_EXECUTION_ID=?", job.getId());
        assertThatThrownBy(() -> recover(snapshot, "fixture-version-drift")).isInstanceOf(IllegalStateException.class);
        assertThat(status(job.getId())).isEqualTo("STARTED");
        assertThat(audits(job.getId())).isZero();
    }

    @Test
    void job_version이_같아도_step_snapshot이_바뀌면_복구를_거부한다() throws Exception {
        JobExecution job = orphan(LocalDate.of(2020, 3, 3));
        var step = new StepExecution("statisticsStep", job);
        step.setStatus(BatchStatus.STARTED);
        step.setStartTime(LocalDateTime.now());
        jobs.add(step);
        var snapshot = preview(job.getId());
        jdbc.update("UPDATE BATCH_STEP_EXECUTION SET VERSION=VERSION+1 WHERE STEP_EXECUTION_ID=?", step.getId());
        assertThatThrownBy(() -> recover(snapshot, "fixture-step-drift")).isInstanceOf(IllegalStateException.class);
        assertThat(status(job.getId())).isEqualTo("STARTED");
        assertThat(audits(job.getId())).isZero();
    }

    @Test
    void 감사저장실패는_metadata_FAILED_변경도_함께_rollback한다() throws Exception {
        JobExecution job = orphan(LocalDate.of(2020, 3, 4));
        var snapshot = preview(job.getId());
        jdbc.update("""
                INSERT INTO statistics_orphan_recovery_audit(job_execution_id,before_version,after_version,
                approval_id,stopped_evidence_ref,snapshot_sha256,snapshot_json) VALUES(?,?,?,'fixture','fixture',?,?)
                """, job.getId(), snapshot.version(), snapshot.version() + 1, snapshot.sha256(), snapshot.json());
        assertThatThrownBy(() -> recover(snapshot, "fixture-audit-failure")).isInstanceOf(java.sql.SQLException.class);
        assertThat(status(job.getId())).isEqualTo("STARTED");
        assertThat(jdbc.queryForObject("SELECT VERSION FROM BATCH_JOB_EXECUTION WHERE JOB_EXECUTION_ID=?", Long.class, job.getId()))
                .isEqualTo(snapshot.version());
    }

    private StatisticsOrphanRecovery.Snapshot preview(long executionId) throws Exception {
        try (var connection = dataSource.getConnection()) {
            return StatisticsOrphanRecovery.preview(connection, executionId);
        }
    }

    private void recover(StatisticsOrphanRecovery.Snapshot snapshot, String evidence) throws Exception {
        try (var connection = dataSource.getConnection()) {
            StatisticsOrphanRecovery.recover(connection, snapshot.executionId(), snapshot.version(), snapshot.sha256(),
                    new StatisticsOrphanRecovery.OfflineConfirmation("local-fixture", evidence, true));
        }
    }

    private JobExecution orphan(LocalDate date) throws Exception {
        var job = jobs.createJobExecution("statisticsJob", new JobParametersBuilder().addString("targetDate", date.toString()).toJobParameters());
        job.setStatus(BatchStatus.STARTED);
        job.setStartTime(LocalDateTime.now());
        jobs.update(job);
        return job;
    }

    private String status(long executionId) {
        return jdbc.queryForObject("SELECT STATUS FROM BATCH_JOB_EXECUTION WHERE JOB_EXECUTION_ID=?", String.class, executionId);
    }

    private int audits(long executionId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM statistics_orphan_recovery_audit WHERE job_execution_id=?", Integer.class, executionId);
    }

    private Company company(String name) {
        return companies.save(Company.builder().name(name).crn(UUID.randomUUID().toString()).contact("000")
                .addrRoad("fixture").addrDetail("fixture").memo("fixture").gpsCycle(60).build());
    }

    private long vehicle(Company company) {
        var model = models.save(VehicleModel.builder().name("fixture").manufacturer("fixture").modelYear(2020).build());
        return vehicles.save(Vehicle.builder().company(company).vehicleModel(model).carNumber(UUID.randomUUID().toString())
                .color("white").mileage(0).powerOn(false).build()).getId();
    }

    private void trip(long vehicle, LocalDateTime start, LocalDateTime end) {
        jdbc.update("""
                INSERT INTO trip_log(vehicle_id,active,created_at,start_time,end_time,total_trip_meter)
                VALUES(?,1,CURRENT_TIMESTAMP(6),?,?,1000)
                """, vehicle, start, end);
    }

    private static final class CrashProcess implements AutoCloseable {
        final Process process;
        final LinkedBlockingQueue<String> markers = new LinkedBlockingQueue<>();

        CrashProcess(LocalDate date, long first, long second) throws Exception {
            var builder = new ProcessBuilder(ProcessHandle.current().info().command().orElseThrow(),
                    "-cp", System.getProperty("statistics.crash.classpath"), CrashWorker.class.getName(),
                    date.toString(), Long.toString(first), Long.toString(second)).redirectErrorStream(true);
            builder.environment().put("SPRING_DATASOURCE_URL", jdbcUrl());
            builder.environment().put("SPRING_DATASOURCE_USERNAME", "test");
            builder.environment().put("SPRING_DATASOURCE_PASSWORD", "test");
            builder.environment().put("SPRING_DATASOURCE_DRIVER_CLASS_NAME", "com.mysql.cj.jdbc.Driver");
            process = builder.start();
            Thread.ofVirtual().start(() -> {
                try (BufferedReader reader = process.inputReader()) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("STATISTICS_TEST_")) markers.add(line);
                    }
                } catch (Exception failure) {
                    markers.add("STATISTICS_TEST_READER_FAILED");
                }
            });
        }

        void awaitCheckpoint() throws Exception {
            assertThat(markers.poll(45, TimeUnit.SECONDS)).isEqualTo("STATISTICS_TEST_CHECKPOINT_COMMITTED");
        }

        @Override
        public void close() throws Exception {
            if (process.isAlive()) process.destroyForcibly();
            if (!process.waitFor(10, TimeUnit.SECONDS)) throw new IllegalStateException("Fixture child did not stop");
        }
    }

    public static final class CrashWorker {
        static long first;
        static long second;

        public static void main(String[] args) {
            first = Long.parseLong(args[1]);
            second = Long.parseLong(args[2]);
            try (var context = new SpringApplicationBuilder(ThiswayApplication.class, CrashConfiguration.class)
                    .run("--server.port=0", "--spring.flyway.enabled=true", "--spring.jpa.hibernate.ddl-auto=validate",
                            "--spring.batch.jdbc.initialize-schema=never", "--thisway.statistics.cron=-",
                            "--thisway.statistics.correction-cron=-", "--thisway.trip-address.worker.cron=-",
                            "--spring.jpa.show-sql=false", "--logging.level.root=ERROR")) {
                context.getBean(StatisticBatchConfig.class).runForDate(LocalDate.parse(args[0]));
                System.out.println("STATISTICS_TEST_UNEXPECTED_COMPLETION");
            } catch (Exception failure) {
                System.out.println("STATISTICS_TEST_START_FAILED:" + failure.getClass().getSimpleName());
                System.exit(1);
            }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class CrashConfiguration {
        @Bean
        static BeanPostProcessor crashCheckpointGate() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String name) {
                    if (bean instanceof CompanyRepository) {
                        var proxy = new ProxyFactory(bean);
                        proxy.addAdvice((MethodInterceptor) call -> call.getMethod().getName().equals("findAllActiveCompanyIds")
                                ? List.of(CrashWorker.first, CrashWorker.second) : call.proceed());
                        return proxy.getProxy();
                    }
                    if (bean instanceof StatisticsCompanyWorker) {
                        var proxy = new ProxyFactory(bean);
                        proxy.setProxyTargetClass(true);
                        proxy.addAdvice((MethodInterceptor) call -> {
                            if (call.getMethod().getName().equals("process") && ((Long) call.getArguments()[1]) == CrashWorker.second) {
                                System.out.println("STATISTICS_TEST_CHECKPOINT_COMMITTED");
                                System.out.flush();
                                new CountDownLatch(1).await(); // The test owns and forcibly terminates this child JVM.
                            }
                            return call.proceed();
                        });
                        return proxy.getProxy();
                    }
                    return bean;
                }
            };
        }
    }
}
