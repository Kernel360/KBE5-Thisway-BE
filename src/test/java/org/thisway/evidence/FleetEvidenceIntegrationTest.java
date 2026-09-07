package org.thisway.evidence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.thisway.company.domain.Company;
import org.thisway.company.infrastructure.CompanyRepository;
import org.thisway.company.statistics.application.StatisticService;
import org.thisway.emulator.domain.Emulator;
import org.thisway.emulator.infrastructure.EmulatorRepository;
import org.thisway.member.domain.Member;
import org.thisway.member.domain.MemberRole;
import org.thisway.member.infrastructure.MemberRepository;
import org.thisway.support.config.RabbitMQConfig;
import org.thisway.vehicle.domain.Vehicle;
import org.thisway.vehicle.infrastructure.VehicleRepository;
import org.thisway.vehicle.log.interfaces.GpsLogEntry;
import org.thisway.vehicle.log.interfaces.GpsLogRequest;
import org.thisway.vehicle.log.interfaces.PowerLogRequest;
import org.thisway.vehicle.triplog.domain.ReverseGeocodeResult;
import org.thisway.vehicle.triplog.domain.ReverseGeocodingConverter;
import org.thisway.vehicle.vehicle_model.domain.VehicleModel;
import org.thisway.vehicle.vehicle_model.infrastructure.VehicleModelRepository;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;

/** Opt-in small-fixture baseline. It deliberately does not run in the normal test task. */
@Tag("fleet-evidence")
@Testcontainers
@DirtiesContext
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.flyway.enabled=true", "spring.flyway.baseline-on-migrate=false",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false",
        "spring.batch.jdbc.initialize-schema=never", "gps-log-collect-mode=rabbitmq",
        "thisway.statistics.cron=-", "thisway.telemetry.requests-per-minute=10000",
        "thisway.statistics.correction-cron=-", "thisway.trip-address.worker.cron=-",
        "logging.level.org.thisway=WARN", "logging.level.org.hibernate.SQL=OFF"})
class FleetEvidenceIntegrationTest {
    private static final long SEED = 20260907L;
    private static final int DEVICES = 8, CONCURRENCY = 4, EXPECTED_GPS = 256;
    private static final LocalDate DATE = LocalDate.of(2026, 9, 1);
    private static final DateTimeFormatter PROTOCOL_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Container static final GenericContainer<?> MYSQL = new GenericContainer<>("mysql:8.0.40")
            .withEnv("MYSQL_DATABASE", "fleet_evidence").withEnv("MYSQL_USER", "test")
            .withEnv("MYSQL_PASSWORD", "test").withEnv("MYSQL_ROOT_PASSWORD", "test-root")
            .withExposedPorts(3306).waitingFor(Wait.forLogMessage(".*ready for connections.*port: 3306.*", 1));
    @Container static final GenericContainer<?> RABBIT = new GenericContainer<>("rabbitmq:3.13.7-alpine")
            .withEnv("RABBITMQ_DEFAULT_USER", "test").withEnv("RABBITMQ_DEFAULT_PASS", "test")
            .withExposedPorts(5672).waitingFor(Wait.forLogMessage(".*Server startup complete.*", 1));
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4.2-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void isolatedServices(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:mysql://" + MYSQL.getHost() + ":"
                + MYSQL.getMappedPort(3306) + "/fleet_evidence?allowPublicKeyRetrieval=true&useSSL=false");
        registry.add("spring.datasource.username", () -> "test");
        registry.add("spring.datasource.password", () -> "test");
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", () -> RABBIT.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", () -> "test");
        registry.add("spring.rabbitmq.password", () -> "test");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired CompanyRepository companies;
    @Autowired MemberRepository members;
    @Autowired VehicleRepository vehicles;
    @Autowired VehicleModelRepository models;
    @Autowired EmulatorRepository emulators;
    @Autowired PasswordEncoder passwords;
    @Autowired StatisticService statistics;
    @Autowired RabbitTemplate rabbit;
    @Autowired RabbitListenerEndpointRegistry listeners;
    @Autowired MeterRegistry meters;
    @MockitoBean ReverseGeocodingConverter geocoding;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    // Credentials never become report fields or assertion values.
    private record Device(Emulator emulator, String key) { }
    private record Sample(String phase, int requestIndex, int status, double latencyMs) { }
    private record Batch(List<Sample> samples, double wallMs, double drainMs, long storedRows) { }

    @Test
    void 실제_업무API와_인증된_GPS파이프라인의_고정fixture_측정결과를_보존한다() throws Exception {
        when(geocoding.convertToAddress(anyDouble(), anyDouble()))
                .thenReturn(new ReverseGeocodeResult("synthetic address", "fixture"));
        // Apply the documented DLX policy to this disposable broker only.
        assertThat(RABBIT.execInContainer("rabbitmqctl", "set_policy", "evidence-dlx", "^gps_log\\.queue$",
                "{\"dead-letter-exchange\":\"gps_log.dead.exchange\",\"dead-letter-routing-key\":\"gps_log.dead\"}",
                "--apply-to", "queues").getExitCode()).isZero();
        List<Company> tenants = List.of(company("A"), company("B"));
        List<String> tokens = List.of(login(tenants.get(0), "a"), login(tenants.get(1), "b"));
        VehicleModel model = models.save(VehicleModel.builder().manufacturer("Synthetic")
                .name("Evidence").modelYear(2026).build());
        List<Device> devices = new ArrayList<>();
        for (int i = 0; i < DEVICES; i++) {
            Vehicle vehicle = vehicles.save(Vehicle.builder().company(tenants.get(i / 4)).vehicleModel(model)
                    .carNumber("FIXTURE-" + i).color("white").mileage(0).powerOn(false).build());
            Emulator emulator = emulators.save(Emulator.builder().vehicle(vehicle).mdn("evidence-" + i)
                    .terminalId("fixture").manufactureId(1).packetVersion(1).deviceId(1).deviceFirmwareVersion("1").build());
            JsonNode issued = successful(post("/api/emulators/" + emulator.getId() + "/device-key", null, tokens.get(i / 4)));
            String key = issued.path("key").asText();
            assertThat(key.isEmpty()).as("device key was issued").isFalse();
            devices.add(new Device(emulator, key));
        }

        Batch warmup = gpsBatch("warmup", devices, 0, 2, 16);
        List<Batch> rounds = new ArrayList<>();
        for (int round = 0; round < 3; round++) {
            rounds.add(gpsBatch("measured-" + (round + 1), devices, 2 + round * 10, 10, 16 + 80 * (round + 1)));
        }
        Batch duplicates = gpsBatch("duplicate-observation", devices, 2, 3, EXPECTED_GPS);
        List<Sample> measured = rounds.stream().flatMap(value -> value.samples().stream()).toList();
        Map<String, Object> workflow = workflow(tenants, tokens, devices);

        long settleStarted = System.nanoTime();
        listeners.stop(); // Wait for all handler invocations and acknowledgements before final counts.
        double settleMs = elapsedMs(settleStarted);
        RabbitAdmin admin = new RabbitAdmin(rabbit);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("schemaVersion", 1);
        report.put("change", "CHANGE-041");
        report.put("measuredAt", Instant.now().toString());
        report.put("compiledClassesSha256", compiledClassesDigest());
        report.put("fixture", Map.of("seed", SEED, "date", DATE.toString(), "companies", 2,
                "devices", DEVICES, "observationsPerRequest", 1, "concurrency", CONCURRENCY,
                "warmupRequests", 16, "measuredRequests", 240, "duplicateRequests", 24,
                "requestsPerDevicePerMinuteLimit", 10000));
        report.put("environment", Map.of("java", System.getProperty("java.runtime.version"),
                "os", System.getProperty("os.name") + " " + System.getProperty("os.version"),
                "architecture", System.getProperty("os.arch"), "availableProcessors", Runtime.getRuntime().availableProcessors(),
                "jvmMaxHeapBytes", Runtime.getRuntime().maxMemory(), "mysql", MYSQL.getDockerImageName(),
                "rabbitmq", RABBIT.getDockerImageName(), "redis", REDIS.getDockerImageName()));
        report.put("http", summary(measured, rounds.stream().mapToDouble(Batch::wallMs).sum()));
        report.put("rounds", rounds.stream().map(this::batchReport).toList());
        report.put("warmup", batchReport(warmup));
        report.put("duplicates", batchReport(duplicates));
        report.put("final", Map.of("expectedGpsRows", EXPECTED_GPS, "actualGpsRows", gpsCount(),
                "extraRowsFrom24DuplicateObservations", gpsCount() - EXPECTED_GPS,
                "readyMessages", ready(admin, RabbitMQConfig.GPS_LOG_QUEUE),
                "deadLetters", ready(admin, RabbitMQConfig.GPS_LOG_DLQ),
                "consumerRejected", counter("gps.consumer.rejected"),
                "storageConfirmed", counter("gps.publisher.storage.confirmed"),
                "storageUnconfirmed", counter("gps.publisher.storage.unconfirmed"),
                "broadcastUnconfirmed", counter("gps.publisher.broadcast.unconfirmed"),
                "listenerStopAndAckWaitMs", settleMs));
        report.put("workflow", workflow);
        report.put("gpsObservationCountQuery", queryEvidence(tenants.getFirst().getId()));
        report.put("samples", measured);
        report.put("limitations", List.of("Small synthetic local fixture; not production capacity or an SLA.",
                "Same-run rounds form a baseline, not a before/after optimization comparison.",
                "Docker Desktop CPU/memory quotas and background host load are not pinned by this test.",
                "HTTP latency measures broker acceptance, not DB commit latency.",
                "Drain time is request-batch completion to expected row count and ready-queue zero; it is not per-message lag.",
                "Redis admission protection limit is raised to 10000 per device/minute for measurement.",
                "API workflow is verified; full frontend screen workflow and independent human explanation remain separate.",
                "Reverse geocoding is stubbed; all GPS coordinates are synthetic.",
                "No application crash, broker restart, saturation or external deployment was performed."));
        Path output = Path.of("build/reports/fleet-evidence/result.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, json.writerWithDefaultPrettyPrinter().writeValueAsString(report) + "\n");
        assertThat(measured).allMatch(sample -> sample.status() == 200);
        assertThat(warmup.samples()).allMatch(sample -> sample.status() == 200);
        assertThat(duplicates.samples()).allMatch(sample -> sample.status() == 200);
        assertThat(gpsCount()).isEqualTo(EXPECTED_GPS);
        assertThat(ready(admin, RabbitMQConfig.GPS_LOG_QUEUE)).isZero();
        assertThat(ready(admin, RabbitMQConfig.GPS_LOG_DLQ)).isZero();
        assertThat(counter("gps.consumer.rejected")).isZero();
        assertThat(counter("gps.publisher.storage.unconfirmed")).isZero();
        assertThat(counter("gps.publisher.storage.confirmed")).isEqualTo(280);
        assertThat(counter("gps.publisher.broadcast.unconfirmed")).isZero();
    }

    private Company company(String label) {
        return companies.save(Company.builder().name("Synthetic " + label).crn("EVIDENCE-" + label)
                .contact("000").addrRoad("fixture").addrDetail("fixture").memo("synthetic only").gpsCycle(60).build());
    }

    private String login(Company company, String suffix) throws Exception {
        String password = "local-fixture-password";
        String email = "fleet-" + suffix + "@example.test";
        members.save(Member.builder().company(company).role(MemberRole.COMPANY_ADMIN).name("Synthetic " + suffix)
                .email(email).password(passwords.encode(password)).phone("01000000000").memo("fixture").build());
        JsonNode response = successful(post("/api/auth/login", Map.of("email", email, "password", password), null));
        String token = response.path("token").asText();
        assertThat(token.isEmpty()).as("real login produced an access token").isFalse();
        return token;
    }

    private Batch gpsBatch(String phase, List<Device> devices, int firstSequence, int perDevice, long expected) throws Exception {
        List<Callable<Sample>> work = new ArrayList<>();
        for (int sequence = firstSequence; sequence < firstSequence + perDevice; sequence++) {
            for (int device = 0; device < DEVICES; device++) {
                Device fixture = devices.get(device);
                int index = sequence * DEVICES + device;
                LocalDateTime time = DATE.atTime(10, 0).plusSeconds(sequence);
                var payload = new GpsLogRequest(fixture.emulator().getMdn(), "fixture", "1", "1", "1",
                        PROTOCOL_TIME.format(time), "1", List.of(new GpsLogEntry(null, null, "A", "37000000",
                        "127000000", "90", "20", Integer.toString(1000 + sequence), "12")));
                work.add(() -> {
                    long started = System.nanoTime();
                    try { return new Sample(phase, index, telemetry("gps", payload, fixture).statusCode(), elapsedMs(started)); }
                    catch (Exception failure) { return new Sample(phase, index, 0, elapsedMs(started)); }
                });
            }
        }
        Collections.shuffle(work, new Random(SEED + firstSequence));
        long started = System.nanoTime();
        List<Sample> samples = new ArrayList<>();
        try (var workers = Executors.newFixedThreadPool(CONCURRENCY)) {
            for (var future : workers.invokeAll(work)) samples.add(future.get());
        }
        double wallMs = elapsedMs(started);
        long drainStarted = System.nanoTime();
        RabbitAdmin admin = new RabbitAdmin(rabbit);
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(20))
                .until(() -> gpsCount() == expected && ready(admin, RabbitMQConfig.GPS_LOG_QUEUE) == 0);
        return new Batch(List.copyOf(samples), wallMs, elapsedMs(drainStarted), gpsCount());
    }

    private Map<String, Object> workflow(List<Company> tenants, List<String> tokens, List<Device> devices) throws Exception {
        Device device = devices.getFirst();
        successful(telemetry("power", new PowerLogRequest(device.emulator().getMdn(), "fixture", "1", "1", "1",
                "20260901100000", "", "A", "37000000", "127000000", "0", "0", "1000"), device));
        successful(telemetry("power", new PowerLogRequest(device.emulator().getMdn(), "fixture", "1", "1", "1",
                "20260901100000", "20260901120000", "A", "37000000", "127000000", "0", "0", "1500"), device));
        // Internal aggregation API, as the scheduler does; company administrators cannot invoke the operator endpoint.
        for (Company company : tenants) statistics.saveStatistics(company.getId(), DATE);
        JsonNode ownVehicles = successful(get("/api/vehicles?size=10", tokens.getFirst()));
        assertThat(ownVehicles.path("totalElements").asInt()).isEqualTo(4);
        JsonNode ownVehicle = successful(get("/api/vehicles/" + device.emulator().getVehicle().getId(), tokens.getFirst()));
        assertThat(ownVehicle.path("mileage").asInt()).isEqualTo(1500);
        JsonNode trips = successful(get("/api/trip-log?size=10", tokens.getFirst()));
        assertThat(trips.path("totalElements").asInt()).isEqualTo(1);
        long tripId = jdbc.queryForObject("SELECT id FROM trip_log WHERE vehicle_id=?", Long.class, device.emulator().getVehicle().getId());
        successful(get("/api/trip-log/detail/" + tripId, tokens.getFirst()));
        String statsPath = "/api/statistics?startDate=" + DATE + "&endDate=" + DATE;
        JsonNode a = successful(get(statsPath, tokens.getFirst()));
        JsonNode b = successful(get(statsPath, tokens.get(1)));
        assertThat(a.path("companyId").asLong()).isEqualTo(tenants.getFirst().getId());
        assertThat(a.path("totalDrivingTime").asInt()).isEqualTo(120);
        assertThat(a.path("quality").path("gpsObservationCount").asInt()).isEqualTo(128);
        assertThat(b.path("companyId").asLong()).isEqualTo(tenants.get(1).getId());
        assertThat(b.path("totalDrivingTime").asInt()).isZero();
        assertThat(b.path("quality").path("gpsObservationCount").asInt()).isEqualTo(128);
        int vehicleDenied = get("/api/vehicles/" + device.emulator().getVehicle().getId(), tokens.get(1)).statusCode();
        int tripDenied = get("/api/trip-log/detail/" + tripId, tokens.get(1)).statusCode();
        assertThat(vehicleDenied).isEqualTo(404);
        assertThat(tripDenied).isEqualTo(404);
        return Map.of("realLoginCompanies", 2, "companyAVehicles", 4, "companyATrips", 1,
                "tripDistanceMeters", jdbc.queryForObject("SELECT distance_meters FROM trip_log WHERE id=?", Integer.class, tripId),
                "companyADrivingMinutes", 120, "companyBDrivingMinutes", 0,
                "gpsObservationsPerCompany", 128, "foreignVehicleHttpStatus", vehicleDenied,
                "foreignTripHttpStatus", tripDenied, "frontendScreensVerified", false);
    }

    private Map<String, Object> queryEvidence(long companyId) {
        String sql = "SELECT COUNT(*) FROM gps_log g JOIN vehicle v ON v.id=g.vehicle_id "
                + "WHERE v.company_id=? AND v.active=true AND g.occurred_time>=? AND g.occurred_time<?";
        List<Double> samples = new ArrayList<>();
        for (int i = 0; i < 5; i++) jdbc.queryForObject(sql, Long.class, companyId, DATE.atStartOfDay(), DATE.plusDays(1).atStartOfDay());
        for (int i = 0; i < 30; i++) {
            long started = System.nanoTime();
            long rows = jdbc.queryForObject(sql, Long.class, companyId, DATE.atStartOfDay(), DATE.plusDays(1).atStartOfDay());
            assertThat(rows).isEqualTo(128);
            samples.add(elapsedMs(started));
        }
        List<String> plan = jdbc.query("EXPLAIN ANALYZE " + sql, (row, index) -> row.getString(1),
                companyId, DATE.atStartOfDay(), DATE.plusDays(1).atStartOfDay());
        return Map.of("sql", sql, "sampleCount", 30, "warmupCount", 5,
                "p50Ms", percentile(samples, .5), "p95Ms", percentile(samples, .95), "p99Ms", percentile(samples, .99),
                "jdbcRoundTripSamplesMs", samples, "explainAnalyze", plan);
    }

    private HttpResponse<String> telemetry(String kind, Object payload, Device device) throws Exception {
        return http.send(base("/api/logs/" + kind).header("X-Device-Id", device.emulator().getId().toString())
                .header("X-Device-Key", device.key()).header("X-Request-Id", UUID.randomUUID().toString())
                .header("X-Request-Timestamp", Long.toString(Instant.now().getEpochSecond()))
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload))).build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> post(String path, Object payload, String token) throws Exception {
        var request = base(path);
        if (token != null) request.header("Authorization", "Bearer " + token);
        return http.send(request.POST(HttpRequest.BodyPublishers.ofString(payload == null ? "" : json.writeValueAsString(payload))).build(),
                HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> get(String path, String token) throws Exception {
        return http.send(base(path).header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpRequest.Builder base(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json");
    }
    private JsonNode successful(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).as("expected HTTP success without echoing body or credentials").isEqualTo(200);
        return json.readTree(response.body());
    }
    private long gpsCount() { return jdbc.queryForObject("SELECT COUNT(*) FROM gps_log", Long.class); }
    private long ready(RabbitAdmin admin, String queue) {
        var info = admin.getQueueInfo(queue);
        if (info == null) throw new IllegalStateException("Evidence queue missing");
        return info.getMessageCount();
    }
    private double counter(String name) {
        var counter = meters.find(name).counter();
        return counter == null ? 0 : counter.count();
    }
    private Map<String, Object> batchReport(Batch batch) {
        return Map.of("http", summary(batch.samples(), batch.wallMs()), "drainAfterRequestsMs", batch.drainMs(),
                "actualGpsRows", batch.storedRows());
    }
    private Map<String, Object> summary(List<Sample> samples, double wallMs) {
        List<Double> latencies = samples.stream().map(Sample::latencyMs).toList();
        long errors = samples.stream().filter(sample -> sample.status() != 200).count();
        return Map.of("requests", samples.size(), "errors", errors, "errorRate", (double) errors / samples.size(),
                "p50Ms", percentile(latencies, .50), "p95Ms", percentile(latencies, .95), "p99Ms", percentile(latencies, .99),
                "wallMs", wallMs, "requestsPerSecond", samples.size() / (wallMs / 1000));
    }
    private static double percentile(List<Double> values, double quantile) {
        List<Double> sorted = values.stream().sorted().toList();
        return sorted.get(Math.max(0, (int) Math.ceil(sorted.size() * quantile) - 1));
    }
    private static double elapsedMs(long started) { return (System.nanoTime() - started) / 1_000_000.0; }

    private static String compiledClassesDigest() throws Exception {
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        Path root = Path.of("build/classes/java");
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".class")).sorted().toList()) {
                digest.update(root.relativize(path).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                digest.update(Files.readAllBytes(path));
            }
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }
}
