package org.thisway.evidence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
import org.thisway.support.security.utils.JwtTokenProvider;
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
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;

@Tag("fleet-browser")
@Testcontainers
@DirtiesContext
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.flyway.enabled=true", "spring.flyway.baseline-on-migrate=false",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false",
        "spring.batch.jdbc.initialize-schema=never", "gps-log-collect-mode=rabbitmq",
        "thisway.statistics.cron=-", "thisway.statistics.correction-cron=-",
        "thisway.trip-address.worker.cron=-", "logging.level.org.thisway=WARN"})
class FleetBrowserIntegrationTest {
    private static final LocalDate DATE = LocalDate.of(2026, 9, 1);
    private static final String PASSWORD = "local-browser-fixture-only";
    @Container static final GenericContainer<?> MYSQL = new GenericContainer<>("mysql:8.0.40")
            .withEnv("MYSQL_DATABASE", "fleet_browser").withEnv("MYSQL_USER", "test")
            .withEnv("MYSQL_PASSWORD", "test").withEnv("MYSQL_ROOT_PASSWORD", "test-root")
            .withExposedPorts(3306).waitingFor(Wait.forLogMessage(".*ready for connections.*port: 3306.*", 1));
    @Container static final GenericContainer<?> RABBIT = new GenericContainer<>("rabbitmq:3.13.7-alpine")
            .withEnv("RABBITMQ_DEFAULT_USER", "test").withEnv("RABBITMQ_DEFAULT_PASS", "test")
            .withExposedPorts(5672).waitingFor(Wait.forLogMessage(".*Server startup complete.*", 1));
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4.2-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void isolatedServices(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306)
                + "/fleet_browser?allowPublicKeyRetrieval=true&useSSL=false");
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

    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired CompanyRepository companies;
    @Autowired MemberRepository members;
    @Autowired VehicleRepository vehicles;
    @Autowired VehicleModelRepository models;
    @Autowired EmulatorRepository emulators;
    @Autowired PasswordEncoder passwords;
    @Autowired StatisticService statistics;
    @Autowired JwtTokenProvider tokens;
    @MockitoBean ReverseGeocodingConverter geocoding;
    @LocalServerPort int port;
    @TempDir Path temporary;
    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void 실제_회사별_로그인과_차량_운행_통계_화면을_MySQL_API에_연결한다() throws Exception {
        when(geocoding.convertToAddress(anyDouble(), anyDouble()))
                .thenReturn(new ReverseGeocodeResult("합성 주소", "fixture"));
        Company a = company("A"), b = company("B");
        Member memberA = member(a, "a"), memberB = member(b, "b");
        VehicleModel model = models.save(VehicleModel.builder().manufacturer("Synthetic").name("Browser").modelYear(2026).build());
        Vehicle vehicleA = vehicle(a, model, "FLEET-A"), vehicleB = vehicle(b, model, "FLEET-B");
        Emulator emulator = emulators.save(Emulator.builder().vehicle(vehicleA).mdn("browser-device-a")
                .terminalId("fixture").manufactureId(1).packetVersion(1).deviceId(1).deviceFirmwareVersion("1").build());
        String adminToken = tokens.generateAccessToken(memberA.getEmail(), Map.of("companyId", a.getId(), "roles", List.of("COMPANY_ADMIN")));
        var issued = http.send(HttpRequest.newBuilder(URI.create(base() + "/api/emulators/" + emulator.getId() + "/device-key"))
                .header("Authorization", "Bearer " + adminToken).POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(issued.statusCode()).isEqualTo(200);
        String key = json.readTree(issued.body()).path("key").asText();
        assertThat(key.isEmpty()).as("fixture device key issued").isFalse();
        telemetry("power", new PowerLogRequest(emulator.getMdn(), "fixture", "1", "1", "1",
                "20260901100000", "", "A", "37000000", "127000000", "0", "0", "1000"), emulator, key);
        for (String time : List.of("20260901101500", "20260901110000")) {
            telemetry("gps", new GpsLogRequest(emulator.getMdn(), "fixture", "1", "1", "1", time, "1",
                    List.of(new GpsLogEntry(null, null, "A", "37000000", "127000000", "0", "20", "1200", "12"))), emulator, key);
        }
        telemetry("power", new PowerLogRequest(emulator.getMdn(), "fixture", "1", "1", "1",
                "20260901100000", "20260901120000", "A", "37000000", "127000000", "0", "0", "1500"), emulator, key);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM gps_log", Long.class)).isEqualTo(2));
        statistics.saveStatistics(a.getId(), DATE);
        statistics.saveStatistics(b.getId(), DATE);
        long tripId = jdbc.queryForObject("SELECT id FROM trip_log WHERE vehicle_id=?", Long.class, vehicleA.getId());

        Path privateFixture = temporary.resolve("fixture.json");
        Files.createFile(privateFixture, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        Files.writeString(privateFixture, json.writeValueAsString(Map.of("date", DATE.toString(),
                "a", Map.of("email", memberA.getEmail(), "password", PASSWORD, "carNumber", vehicleA.getCarNumber(),
                        "vehicleId", vehicleA.getId(), "tripId", tripId),
                "b", Map.of("email", memberB.getEmail(), "password", PASSWORD, "carNumber", vehicleB.getCarNumber()))));
        Path output = Path.of("build/reports/fleet-browser").toAbsolutePath();
        Files.createDirectories(output);
        var builder = new ProcessBuilder("node", "tests/live-fleet/workflow.mjs")
                .directory(Path.of(System.getProperty("fleet.fe.path")).toFile())
                .redirectErrorStream(true).redirectOutput(output.resolve("browser-process.log").toFile());
        builder.environment().put("FLEET_FIXTURE_FILE", privateFixture.toString());
        builder.environment().put("FLEET_BACKEND_URL", base());
        builder.environment().put("FLEET_EVIDENCE_OUTPUT", output.toString());
        Process browser = builder.start();
        try {
            assertThat(browser.waitFor(90, TimeUnit.SECONDS)).as("fleet browser workflow timeout").isTrue();
            assertThat(browser.exitValue()).as("fleet browser failed; inspect local build/reports/fleet-browser/browser-process.log").isZero();
            var report = json.readTree(Files.readString(output.resolve("result.json")));
            assertThat(report.path("passed").asBoolean()).isTrue();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM gps_log", Long.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT distance_meters FROM trip_log WHERE id=?", Integer.class, tripId)).isEqualTo(500);
        } finally {
            if (browser.isAlive()) { browser.destroyForcibly(); browser.waitFor(5, TimeUnit.SECONDS); }
            Files.deleteIfExists(privateFixture);
        }
    }

    private Company company(String label) {
        return companies.save(Company.builder().name("Synthetic " + label).crn("BROWSER-" + label)
                .contact("000").addrRoad("fixture").addrDetail("fixture").memo("synthetic only").gpsCycle(60).build());
    }
    private Member member(Company company, String label) {
        return members.save(Member.builder().company(company).role(MemberRole.COMPANY_ADMIN).name("Synthetic " + label)
                .email("browser-" + label + "@example.test").password(passwords.encode(PASSWORD)).phone("01000000000").memo("fixture").build());
    }
    private Vehicle vehicle(Company company, VehicleModel model, String plate) {
        return vehicles.save(Vehicle.builder().company(company).vehicleModel(model).carNumber(plate)
                .color("white").mileage(0).powerOn(false).build());
    }
    private String base() { return "http://127.0.0.1:" + port; }
    private void telemetry(String kind, Object payload, Emulator emulator, String key) throws Exception {
        var response = http.send(HttpRequest.newBuilder(URI.create(base() + "/api/logs/" + kind)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json").header("X-Device-Id", emulator.getId().toString()).header("X-Device-Key", key)
                .header("X-Request-Id", UUID.randomUUID().toString())
                .header("X-Request-Timestamp", Long.toString(Instant.now().getEpochSecond()))
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload))).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("synthetic telemetry accepted").isEqualTo(200);
    }
}
