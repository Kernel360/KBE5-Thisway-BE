package org.thisway.emulator.credential;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.thisway.company.domain.Company;
import org.thisway.company.infrastructure.CompanyRepository;
import org.thisway.emulator.domain.Emulator;
import org.thisway.emulator.infrastructure.EmulatorRepository;
import org.thisway.member.domain.Member;
import org.thisway.member.domain.MemberRole;
import org.thisway.member.infrastructure.MemberRepository;
import org.thisway.support.security.utils.JwtTokenProvider;
import org.thisway.vehicle.domain.Vehicle;
import org.thisway.vehicle.infrastructure.VehicleRepository;
import org.thisway.vehicle.vehicle_model.domain.VehicleModel;
import org.thisway.vehicle.vehicle_model.infrastructure.VehicleModelRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@org.testcontainers.junit.jupiter.Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.batch.jdbc.initialize-schema=never"})
@AutoConfigureMockMvc
@DirtiesContext
class DeviceCredentialIntegrationTest {
    @org.testcontainers.junit.jupiter.Container
    static final org.testcontainers.containers.GenericContainer<?> MYSQL =
            new org.testcontainers.containers.GenericContainer<>("mysql:8.0.40")
                    .withEnv("MYSQL_DATABASE", "credentials_test").withEnv("MYSQL_USER", "test")
                    .withEnv("MYSQL_PASSWORD", "test").withEnv("MYSQL_ROOT_PASSWORD", "test-root")
                    .withCommand("--log-bin-trust-function-creators=1") // Disposable rollback trigger fixture only.
                    .withExposedPorts(3306).waitingFor(org.testcontainers.containers.wait.strategy.Wait
                            .forLogMessage(".*ready for connections.*port: 3306.*", 1));
    @org.testcontainers.junit.jupiter.Container
    static final org.testcontainers.containers.GenericContainer<?> RABBIT =
            new org.testcontainers.containers.GenericContainer<>("rabbitmq:3.13.7-alpine")
                    .withEnv("RABBITMQ_DEFAULT_USER", "test").withEnv("RABBITMQ_DEFAULT_PASS", "test")
                    .withExposedPorts(5672).waitingFor(org.testcontainers.containers.wait.strategy.Wait
                            .forLogMessage(".*Server startup complete.*", 1));

    @org.testcontainers.junit.jupiter.Container
    static final org.testcontainers.containers.GenericContainer<?> REDIS =
            new org.testcontainers.containers.GenericContainer<>("redis:7.4.2-alpine").withExposedPorts(6379);

    @org.springframework.test.context.DynamicPropertySource
    static void database(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.datasource.url", () -> "jdbc:mysql://" + MYSQL.getHost() + ":"
                + MYSQL.getMappedPort(3306) + "/credentials_test?allowPublicKeyRetrieval=true&useSSL=false");
        registry.add("spring.datasource.username", () -> "test");
        registry.add("spring.datasource.password", () -> "test");
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }
    @org.springframework.boot.test.web.server.LocalServerPort int serverPort;
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDirectory;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenProvider tokens;
    @Autowired CompanyRepository companies;
    @Autowired VehicleModelRepository models;
    @Autowired VehicleRepository vehicles;
    @Autowired MemberRepository members;
    @Autowired EmulatorRepository emulators;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    org.thisway.vehicle.triplog.domain.ReverseGeocodingConverter geocoding;
    @Autowired DeviceAuthenticationService authentication;
    @Autowired org.thisway.vehicle.log.application.TelemetryRequestGuard requestGuard;
    @Autowired org.thisway.vehicle.log.application.GpsLogSaveService gpsSave;
    @Autowired org.thisway.vehicle.log.application.DeviceTelemetryService telemetry;
    @Autowired DeviceBindingGuard guard;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @MockitoSpyBean org.thisway.vehicle.triplog.application.StreamCoordinatesService streams;
    @MockitoSpyBean DeviceCredentialRepository repository;
    private Company company;
    private Member admin;
    private Emulator device;
    private String token;

    @BeforeEach
    void fixture() {
        when(geocoding.convertToAddress(anyDouble(), anyDouble())).thenReturn(
                new org.thisway.vehicle.triplog.domain.ReverseGeocodeResult("fixture", "fixture"));
        company = company();
        admin = members.save(Member.builder().company(company).role(MemberRole.COMPANY_ADMIN).name("fixture")
                .email(UUID.randomUUID() + "@example.test").password("fixture-unused-password").phone("01000000000").memo("fixture").build());
        device = device(company);
        token = token("COMPANY_ADMIN");
    }

    @Test
    void 원문은_발급응답뿐이고_DB에는_해시_연결_만료와_감사기록을_저장한다() throws Exception {
        String key = issue(device.getId(), token);
        assertThat(key).matches("twdev_[A-Za-z0-9_-]{43}");
        assertThat(hash()).isEqualTo(DeviceKeyMaterial.hash(key)).isNotEqualTo(key);
        assertThat(jdbc.queryForMap("SELECT bound_vehicle_id,bound_company_id,bound_mdn FROM device_credential WHERE emulator_id=?",
                device.getId())).containsAllEntriesOf(Map.of("bound_vehicle_id", device.getVehicle().getId(),
                "bound_company_id", company.getId(), "bound_mdn", device.getMdn()));
        assertThat(jdbc.queryForObject("SELECT TIMESTAMPDIFF(DAY,issued_at,expires_at) FROM device_credential WHERE emulator_id=?",
                Integer.class, device.getId())).isEqualTo(30);
        assertThat(new IssuedDeviceKey(key, Instant.now()).toString()).doesNotContain(key);
        mvc.perform(get(path(device.getId())).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("ACTIVE"))
                .andExpect(jsonPath("$.key").doesNotExist()).andExpect(jsonPath("$.keyHash").doesNotExist());
        assertThat(events()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT actor_member_id FROM device_credential_event WHERE emulator_id=?",
                Long.class, device.getId())).isEqualTo(admin.getId());
    }

    @Test
    void 교체와_반복폐기는_현재해시_하나와_실제변경_감사만_남긴다() throws Exception {
        String old = issue(device.getId(), token);
        String replacement = issue(device.getId(), token);
        assertThat(replacement).isNotEqualTo(old);
        assertThat(hash()).isEqualTo(DeviceKeyMaterial.hash(replacement));
        assertThat(events()).isEqualTo(2);
        for (int i = 0; i < 2; i++) mvc.perform(delete(path(device.getId())).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        assertThat(hash()).isNull();
        assertThat(events()).isEqualTo(3);
        expectState("REVOKED");
    }

    @Test
    void 다른회사의_키는_발급_조회_폐기할_수_없다() throws Exception {
        var other = device(company());
        for (var request : List.of(post(path(other.getId())), get(path(other.getId())), delete(path(other.getId())))) {
            mvc.perform(request.header("Authorization", "Bearer " + token)).andExpect(status().isNotFound());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM device_credential WHERE emulator_id=?", Integer.class,
                other.getId())).isZero();
    }

    @Test
    void 비인증과_일반회원_운영담당자는_세_관리API에_접근할_수_없다() throws Exception {
        for (String role : List.of("MEMBER", "COMPANY_CHEF")) {
            for (var request : List.of(post(path(device.getId())), get(path(device.getId())), delete(path(device.getId())))) {
                mvc.perform(request.header("Authorization", "Bearer " + token(role))).andExpect(status().isForbidden());
            }
        }
        for (var request : List.of(post(path(device.getId())), get(path(device.getId())), delete(path(device.getId())))) {
            mvc.perform(request).andExpect(status().isUnauthorized());
        }
        assertThat(events()).isZero();
    }

    @Test
    void JWT가_관리자여도_DB에서_권한이_낮아졌으면_발급을_막는다() throws Exception {
        jdbc.update("UPDATE member SET role='MEMBER' WHERE id=?", admin.getId());
        mvc.perform(post(path(device.getId())).header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        assertThat(events()).isZero();
    }

    @Test
    void 만료와_발급당시_연결변경을_상태조회에서_구분한다() throws Exception {
        expectState("NOT_ISSUED");
        issue(device.getId(), token);
        jdbc.update("UPDATE device_credential SET issued_at='2020-01-01',expires_at='2020-01-31' WHERE emulator_id=?", device.getId());
        expectState("EXPIRED");
        issue(device.getId(), token);
        jdbc.update("UPDATE emulator SET mdn=? WHERE id=?", "changed-" + device.getId(), device.getId());
        expectState("BINDING_CHANGED");
        issue(device.getId(), token);
        expectState("ACTIVE");
        var otherVehicle = device(company).getVehicle();
        jdbc.update("UPDATE emulator SET vehicle_id=? WHERE id=?", otherVehicle.getId(), device.getId());
        expectState("BINDING_CHANGED");
    }

    @Test
    void 동시교체_네번은_장치당_한개의_최종키와_네개의_감사를_남긴다() throws Exception {
        var executor = java.util.concurrent.Executors.newFixedThreadPool(4);
        var ready = new java.util.concurrent.CountDownLatch(4);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<String>>();
            for (int i = 0; i < 4; i++) futures.add(executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout");
                return issue(device.getId(), token);
            }));
            assertThat(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var hashes = new java.util.HashSet<String>();
            for (var future : futures) hashes.add(DeviceKeyMaterial.hash(future.get(15, java.util.concurrent.TimeUnit.SECONDS)));
            assertThat(hashes).hasSize(4).contains(hash());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM device_credential WHERE emulator_id=?", Integer.class, device.getId())).isEqualTo(1);
            assertThat(events()).isEqualTo(4);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void 감사저장이_실패하면_키교체도_rollback된다() throws Exception {
        String original = issue(device.getId(), token);
        doThrow(new IllegalStateException("fixture audit failure")).when(repository).audit(any(), anyLong(), eq("ISSUED"), any());
        mvc.perform(post(path(device.getId())).header("Authorization", "Bearer " + token)).andExpect(status().isInternalServerError());
        assertThat(hash()).isEqualTo(DeviceKeyMaterial.hash(original));
        assertThat(events()).isEqualTo(1);
    }

    @Test
    void 장치삭제는_키를_제거하지만_기존_감사기록은_지우지_않는다() throws Exception {
        issue(device.getId(), token);
        emulators.deleteById(device.getId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM device_credential WHERE emulator_id=?", Integer.class, device.getId())).isZero();
        assertThat(events()).isEqualTo(1);
    }

    @Test
    void 다른장치와_해시가_충돌해도_기존_키를_덮어쓰지_않는다() throws Exception {
        issue(device.getId(), token);
        String before = hash();
        var other = device(company);
        var binding = new DeviceCredentialRepository.Binding(other.getId(), other.getVehicle().getId(), company.getId(), other.getMdn(), 0, true);
        assertThatThrownBy(() -> repository.replace(binding, before, Instant.now(), Instant.now().plusSeconds(3600)))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        assertThat(hash()).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM device_credential WHERE emulator_id=?", Integer.class, other.getId())).isZero();
    }

    @Test
    void 차량을_바꿨다가_돌려도_이전키는_다시_ACTIVE가_되지_않는다() throws Exception {
        issue(device.getId(), token);
        var other = device(company).getVehicle();
        update(Map.of("vehicleId", other.getId()));
        expectState("BINDING_CHANGED");
        update(Map.of("vehicleId", device.getVehicle().getId()));
        assertThat(revision()).isEqualTo(2);
        expectState("BINDING_CHANGED");
        issue(device.getId(), token);
        expectState("ACTIVE");
    }

    @Test
    void MDN_복원은_키를_되살리지_않고_동일값과_펌웨어수정은_revision을_유지한다() throws Exception {
        issue(device.getId(), token);
        update(Map.of("mdn", device.getMdn(), "vehicleId", device.getVehicle().getId(), "deviceFirmwareVersion", "2"));
        assertThat(revision()).isZero();
        expectState("ACTIVE");
        update(Map.of("mdn", "changed-" + device.getId()));
        update(Map.of("mdn", device.getMdn()));
        assertThat(revision()).isEqualTo(2);
        expectState("BINDING_CHANGED");
    }

    @Test
    void 비활성차량은_발급을_거부하지만_소유관리자의_조회와_폐기는_허용한다() throws Exception {
        issue(device.getId(), token);
        jdbc.update("UPDATE vehicle SET active=false WHERE id=?", device.getVehicle().getId());
        expectState("INACTIVE");
        mvc.perform(post(path(device.getId())).header("Authorization", "Bearer " + token)).andExpect(status().isNotFound());
        mvc.perform(delete(path(device.getId())).header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        assertThat(hash()).isNull();
        expectState("REVOKED");
        var other = device(company());
        jdbc.update("UPDATE vehicle SET active=false WHERE id=?", other.getVehicle().getId());
        for (var request : List.of(get(path(other.getId())), delete(path(other.getId())))) {
            mvc.perform(request.header("Authorization", "Bearer " + token)).andExpect(status().isNotFound());
        }
    }

    @Test
    void 동시재연결과_발급은_revision증가를_잃지_않고_현재연결의_키만_ACTIVE다() throws Exception {
        issue(device.getId(), token);
        var first = device(company).getVehicle();
        var second = device(company).getVehicle();
        var executor = java.util.concurrent.Executors.newFixedThreadPool(3);
        var ready = new java.util.concurrent.CountDownLatch(3);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            var actions = List.<java.util.concurrent.Callable<Void>>of(
                    () -> { update(Map.of("vehicleId", first.getId())); return null; },
                    () -> { update(Map.of("vehicleId", second.getId())); return null; },
                    () -> { issue(device.getId(), token); return null; });
            var futures = new java.util.ArrayList<java.util.concurrent.Future<Void>>();
            for (var action : actions) futures.add(executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout");
                return action.call();
            }));
            assertThat(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (var future : futures) future.get(15, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(revision()).isEqualTo(2);
            long bound = jdbc.queryForObject("SELECT bound_assignment_revision FROM device_credential WHERE emulator_id=?", Long.class, device.getId());
            assertThat(bound).isBetween(0L, 2L);
            expectState(bound == 2 ? "ACTIVE" : "BINDING_CHANGED");
            if (bound == 2) assertThat(jdbc.queryForObject("SELECT bound_vehicle_id FROM device_credential WHERE emulator_id=?", Long.class, device.getId()))
                    .isEqualTo(jdbc.queryForObject("SELECT vehicle_id FROM emulator WHERE id=?", Long.class, device.getId()));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void 인증은_DB에서_회사_차량_연결세대를_반환하고_비밀을_노출하지_않는다() throws Exception {
        String key = issue(device.getId(), token);
        var identity = authentication.authenticate(device.getId(), key, device.getMdn());
        assertThat(identity).isEqualTo(new DeviceIdentity(device.getId(), device.getVehicle().getId(),
                company.getId(), device.getMdn(), 0));
        assertThat(identity.toString()).doesNotContain(key, device.getMdn(), hash());
        var candidate = repository.findAuthenticationCandidate(device.getId(), Instant.now()).orElseThrow();
        assertThat(candidate.toString()).doesNotContain(key, hash(), device.getMdn());
        assertThat(events()).isEqualTo(1); // Authentication is read-only; no credential changes/audit writes.
    }

    @Test
    void 다른회사_장치의_키와_MDN을_서로_바꾸어_사용할_수_없다() throws Exception {
        String key = issue(device.getId(), token);
        var other = device(company());
        // Another tenant's valid credential; provisioning is fixture-only, not an API bypass.
        String otherKey = DeviceKeyMaterial.generate();
        repository.replace(new DeviceCredentialRepository.Binding(other.getId(), other.getVehicle().getId(),
                other.getVehicle().getCompany().getId(), other.getMdn(), 0, true),
                DeviceKeyMaterial.hash(otherKey), Instant.now().minusSeconds(1), Instant.now().plusSeconds(3600));
        rejectAuthentication(device.getId(), otherKey, device.getMdn());
        rejectAuthentication(other.getId(), key, other.getMdn());
        rejectAuthentication(device.getId(), key, other.getMdn());
        assertThat(authentication.authenticate(other.getId(), otherKey, other.getMdn()).companyId())
                .isEqualTo(other.getVehicle().getCompany().getId());
    }

    @Test
    void 교체_폐기_미발급_삭제_장치의_키는_인증을_거부한다() throws Exception {
        rejectAuthentication(device.getId(), DeviceKeyMaterial.generate(), device.getMdn());
        String old = issue(device.getId(), token);
        String current = issue(device.getId(), token);
        rejectAuthentication(device.getId(), old, device.getMdn());
        assertThat(authentication.authenticate(device.getId(), current, device.getMdn())).isNotNull();
        mvc.perform(delete(path(device.getId())).header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        rejectAuthentication(device.getId(), current, device.getMdn());
        String last = issue(device.getId(), token);
        emulators.deleteById(device.getId());
        rejectAuthentication(device.getId(), last, device.getMdn());
    }

    @Test
    void 만료경계는_배타적이고_미래발급과_폐기시각이_있는_키도_거부한다() throws Exception {
        String key = issue(device.getId(), token);
        var snapshot = repository.find(device.getId()).orElseThrow();
        assertThat(repository.findAuthenticationCandidate(device.getId(), snapshot.issuedAt().minusNanos(1000))).isEmpty();
        assertThat(repository.findAuthenticationCandidate(device.getId(), snapshot.issuedAt())).isPresent();
        assertThat(repository.findAuthenticationCandidate(device.getId(), snapshot.expiresAt().minusNanos(1000))).isPresent();
        assertThat(repository.findAuthenticationCandidate(device.getId(), snapshot.expiresAt())).isEmpty();
        jdbc.update("UPDATE device_credential SET issued_at='2020-01-01',expires_at='2020-01-31' WHERE emulator_id=?", device.getId());
        rejectAuthentication(device.getId(), key, device.getMdn());
        jdbc.update("UPDATE device_credential SET issued_at='2099-01-01',expires_at='2099-01-31' WHERE emulator_id=?", device.getId());
        rejectAuthentication(device.getId(), key, device.getMdn());
        key = issue(device.getId(), token);
        jdbc.update("UPDATE device_credential SET revoked_at=issued_at WHERE emulator_id=?", device.getId());
        rejectAuthentication(device.getId(), key, device.getMdn());
    }

    @Test
    void 차량과_회사가_비활성이면_유효한_키도_인증을_거부한다() throws Exception {
        String key = issue(device.getId(), token);
        jdbc.update("UPDATE vehicle SET active=false WHERE id=?", device.getVehicle().getId());
        rejectAuthentication(device.getId(), key, device.getMdn());
        jdbc.update("UPDATE vehicle SET active=true WHERE id=?", device.getVehicle().getId());
        jdbc.update("UPDATE company SET active=false WHERE id=?", company.getId());
        rejectAuthentication(device.getId(), key, device.getMdn());
    }

    @Test
    void 차량재연결후_원복해도_이전키_인증은_실패하고_새키는_현재세대를_반환한다() throws Exception {
        String old = issue(device.getId(), token);
        update(Map.of("vehicleId", device(company).getVehicle().getId()));
        rejectAuthentication(device.getId(), old, device.getMdn());
        update(Map.of("vehicleId", device.getVehicle().getId()));
        rejectAuthentication(device.getId(), old, device.getMdn());
        String current = issue(device.getId(), token);
        assertThat(authentication.authenticate(device.getId(), current, device.getMdn()).assignmentRevision()).isEqualTo(2);
    }

    @Test
    void MDN은_MySQL_collation과_무관하게_대소문자와_후행공백을_구분한다() throws Exception {
        update(Map.of("mdn", "Device-Mdn"));
        String key = issue(device.getId(), token);
        assertThat(authentication.authenticate(device.getId(), key, "Device-Mdn")).isNotNull();
        rejectAuthentication(device.getId(), key, "device-mdn");
        rejectAuthentication(device.getId(), key, "Device-Mdn ");
        jdbc.update("UPDATE emulator SET mdn='device-mdn' WHERE id=?", device.getId());
        rejectAuthentication(device.getId(), key, "device-mdn");
        jdbc.update("UPDATE emulator SET mdn='Device-Mdn ' WHERE id=?", device.getId());
        rejectAuthentication(device.getId(), key, "Device-Mdn ");
    }

    @Test
    void revision을_우회한_차량과_회사_변경도_발급당시_소속과_다르면_거부한다() throws Exception {
        String key = issue(device.getId(), token);
        var anotherVehicle = device(company).getVehicle();
        jdbc.update("UPDATE emulator SET vehicle_id=? WHERE id=?", anotherVehicle.getId(), device.getId());
        rejectAuthentication(device.getId(), key, device.getMdn());
        jdbc.update("UPDATE emulator SET vehicle_id=? WHERE id=?", device.getVehicle().getId(), device.getId());
        jdbc.update("UPDATE vehicle SET company_id=? WHERE id=?", company().getId(), device.getVehicle().getId());
        rejectAuthentication(device.getId(), key, device.getMdn());
    }

    @Test
    void 세_수집API는_키없음_잘못된키_타장치키와_JWT만으로는_쓰기할수없다() throws Exception {
        String key = issue(device.getId(), token);
        for (String kind : List.of("gps", "power", "geofence")) {
            String body = json.writeValueAsString(packet(kind));
            mvc.perform(post("/api/logs/" + kind).contentType("application/json").content(body))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post("/api/logs/" + kind).header("Authorization", "Bearer " + token)
                    .contentType("application/json").content(body)).andExpect(status().isUnauthorized());
            for (String id : List.of("0", "-1", "9223372036854775808", "1,2", "bad")) {
                mvc.perform(post("/api/logs/" + kind).header("X-Device-Id", id).header("X-Device-Key", key).header("X-Request-Id", UUID.randomUUID().toString()).header("X-Request-Timestamp", Long.toString(Instant.now().getEpochSecond()))
                        .contentType("application/json").content(body)).andExpect(status().isUnauthorized());
            }
            mvc.perform(post("/api/logs/" + kind).header("X-Device-Id", device.getId())
                    .header("X-Device-Key", DeviceKeyMaterial.generate()).contentType("application/json").content(body))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post("/api/logs/" + kind).header("X-Device-Id", device(company()).getId())
                    .header("X-Device-Key", key).header("X-Request-Id", UUID.randomUUID().toString()).header("X-Request-Timestamp", Long.toString(Instant.now().getEpochSecond())).contentType("application/json").content(body))
                    .andExpect(status().isUnauthorized());
            assertThat(logCount(kind)).isZero();
        }
    }

    @Test
    void 실제_DB_insert_rollback은_commit_지연으로_기록되지_않는다() throws Exception {
        var identity = authentication.authenticate(device.getId(), issue(device.getId(), token), device.getMdn());
        var registry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        var latency = new org.thisway.support.logging.GpsCommitLatency(registry, event -> { });
        var consumer = new org.thisway.vehicl_consumer.log.SaveGpsLogConsumer(gpsSave, registry, latency);
        var headers = new java.util.HashMap<String,Object>(org.thisway.vehicle.log.infrastructure.GpsMessageIdentity.headers(identity));
        headers.put(org.thisway.support.logging.GpsCommitLatency.HEADER, System.currentTimeMillis());
        jdbc.execute("CREATE TRIGGER evidence_commit_rollback AFTER INSERT ON gps_log FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'fixture rollback'");
        try {
            assertThatThrownBy(() -> consumer.receiveGpsLog(gpsPacket(), headers)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(logCount("gps")).isZero();
            assertThat(registry.find("gps.admitted.to.commit").timer()).isNull();
            assertThat(registry.find("gps.commit.latency.observations").counter()).isNull();
            assertThat(registry.get("gps.consumer.processing").tag("outcome", "failed").timer().count()).isEqualTo(1);
        } finally { jdbc.execute("DROP TRIGGER evidence_commit_rollback"); registry.close(); }
    }

    @Test
    void 정상키로_세_수집API를_호출하면_인증된_차량에_저장된다() throws Exception {
        String key = issue(device.getId(), token);
        for (String kind : List.of("gps", "power", "geofence")) {
            mvc.perform(post("/api/logs/" + kind).header("X-Device-Id", device.getId())
                    .header("X-Device-Key", key).header("X-Request-Id", UUID.randomUUID().toString()).header("X-Request-Timestamp", Long.toString(Instant.now().getEpochSecond())).contentType("application/json")
                    .content(json.writeValueAsString(packet(kind)))).andExpect(status().isOk());
            assertThat(logCount(kind)).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM trip_log WHERE vehicle_id=?", Integer.class,
                device.getVehicle().getId())).isEqualTo(1);
    }

    @Test
    void 인증후_재연결하면_저장과_방송이_거부되고_원복해도_이전세대는_거부된다() throws Exception {
        var identity = authentication.authenticate(device.getId(), issue(device.getId(), token), device.getMdn());
        update(Map.of("vehicleId", device(company).getVehicle().getId()));
        rejectAdmission(identity);
        update(Map.of("vehicleId", device.getVehicle().getId()));
        rejectAdmission(identity);
        assertThat(logCount("gps")).isZero();
        assertThat(logCount("power")).isZero();
        assertThat(logCount("geofence")).isZero();
        verifyNoInteractions(streams);
    }

    @Test
    void 접수후_키폐기와_만료는_이미접수한_동일연결_메시지를_버리지않는다() throws Exception {
        var identity = authentication.authenticate(device.getId(), issue(device.getId(), token), device.getMdn());
        mvc.perform(delete(path(device.getId())).header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        gpsSave.saveGpsLog(gpsPacket(), identity);
        gpsSave.saveGpsLog(gpsPacket(), identity);
        assertThat(logCount("gps")).isEqualTo(1);
        telemetry.streamGps(gpsPacket(), identity); // Missing min is normalized before SSE conversion.
        verify(streams).sendCurrentCoordinates(eq(new org.thisway.vehicle.domain.VehicleReference(identity.vehicleId(), identity.companyId())), anyList());
    }

    @Test
    void 소속잠금은_저장commit까지_재연결을_대기시킨다() throws Exception {
        var identity = authentication.authenticate(device.getId(), issue(device.getId(), token), device.getMdn());
        var destination = device(company).getVehicle();
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        var started = new java.util.concurrent.CountDownLatch(1);
        var future = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<?>>();
        try {
            new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                guard.requireCurrent(identity, device.getMdn());
                future.set(executor.submit(() -> {
                    started.countDown();
                    update(Map.of("vehicleId", destination.getId()));
                    return null;
                }));
                try {
                    assertThat(started.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                    assertThatThrownBy(() -> future.get().get(200, java.util.concurrent.TimeUnit.MILLISECONDS))
                            .isInstanceOf(java.util.concurrent.TimeoutException.class);
                } catch (InterruptedException error) { throw new RuntimeException(error); }
                gpsSave.saveGpsLog(gpsPacket(), identity);
            });
            future.get().get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(logCount("gps")).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM gps_log WHERE vehicle_id=?", Integer.class, destination.getId())).isZero();
            assertThatThrownBy(() -> gpsSave.saveGpsLog(gpsPacket(), identity)).isInstanceOf(org.thisway.support.common.CustomException.class);
        } finally { executor.shutdownNow(); }
    }

    @Test
    void 실제_HTTP_broker_consumer_경로는_identity를_보존하고_retry와_중복저장을_제한한다() throws Exception {
        String key = issue(device.getId(), token);
        var identity = authentication.authenticate(device.getId(), key, device.getMdn());
        try (var broker = new AuthenticatedBroker(true)) {
            var controller = new org.thisway.vehicle.log.interfaces.LogController(
                    new org.thisway.vehicle.log.application.RabbitMqGpsLogService(broker.producer), authentication, telemetry, requestGuard);
            var http = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller)
                    .setControllerAdvice(new org.thisway.support.common.GlobalExceptionHandler()).build();
            broker.container.start();
            for (int i = 0; i < 2; i++) http.perform(post("/api/logs/gps")
                    .header("X-Device-Id", device.getId()).header("X-Device-Key", key).header("X-Request-Id", UUID.randomUUID().toString()).header("X-Request-Timestamp", Long.toString(Instant.now().getEpochSecond()))
                    .contentType("application/json").content(json.writeValueAsString(gpsPacket())))
                    .andExpect(status().isOk());
            org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted(() -> {
                assertThat(broker.attempts.get()).isEqualTo(4); // First delivery: two transient failures then success.
                assertThat(logCount("gps")).isEqualTo(1);
            });
            var live = broker.template.receive(broker.liveQueue, 1000);
            assertThat(live).isNotNull();
            assertThat(org.thisway.vehicle.log.infrastructure.GpsMessageIdentity.read(live.getMessageProperties().getHeaders(), device.getMdn()))
                    .isEqualTo(identity);
            assertThat(live.toString()).doesNotContain(key, DeviceKeyMaterial.hash(key));
            new org.thisway.vehicl_consumer.log.StreamGpsLogConsumer(telemetry).StreamGpsLog(gpsPacket(), live.getMessageProperties().getHeaders());
            assertThat(broker.template.receive(org.thisway.support.config.RabbitMQConfig.GPS_LOG_DLQ, 100)).isNull();
        }
    }

    @Test
    void 지연된_이전소속과_identity없는_메시지는_retry없이_DLQ로_이동하고_방송하지않는다() throws Exception {
        var identity = authentication.authenticate(device.getId(), issue(device.getId(), token), device.getMdn());
        try (var broker = new AuthenticatedBroker(false)) {
            broker.producer.sendGpsLog(gpsPacket(), identity);
            update(Map.of("vehicleId", device(company).getVehicle().getId()));
            update(Map.of("vehicleId", device.getVehicle().getId()));
            broker.template.convertAndSend(org.thisway.support.config.RabbitMQConfig.GPS_LOG_EXCHANGE,
                    org.thisway.support.config.RabbitMQConfig.GPS_LOG_ROUTING_KEY, gpsPacket());
            broker.container.start();
            var first = broker.template.receive(org.thisway.support.config.RabbitMQConfig.GPS_LOG_DLQ, 10000);
            var second = broker.template.receive(org.thisway.support.config.RabbitMQConfig.GPS_LOG_DLQ, 10000);
            assertThat(first).isNotNull();
            assertThat(second).isNotNull();
            assertThat(broker.attempts.get()).isEqualTo(2);
            assertThat(logCount("gps")).isZero();
            var live = broker.template.receive(broker.liveQueue, 1000);
            assertThatThrownBy(() -> new org.thisway.vehicl_consumer.log.StreamGpsLogConsumer(telemetry)
                    .StreamGpsLog(gpsPacket(), live.getMessageProperties().getHeaders()))
                    .isInstanceOf(org.thisway.support.common.CustomException.class);
            verifyNoInteractions(streams);
        }
    }

    private class AuthenticatedBroker implements AutoCloseable {
        final java.util.concurrent.atomic.AtomicInteger attempts = new java.util.concurrent.atomic.AtomicInteger();
        final org.springframework.amqp.rabbit.connection.CachingConnectionFactory connection;
        final org.springframework.amqp.rabbit.core.RabbitTemplate template;
        final org.springframework.amqp.rabbit.core.RabbitAdmin admin;
        final org.thisway.vehicle.log.infrastructure.GpsLogProducer producer;
        final org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer container;
        final io.micrometer.core.instrument.simple.SimpleMeterRegistry meters = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        final String liveQueue = "identity-live-" + UUID.randomUUID();

        AuthenticatedBroker(boolean transientFailure) {
            connection = new org.springframework.amqp.rabbit.connection.CachingConnectionFactory(RABBIT.getHost(), RABBIT.getMappedPort(5672));
            connection.setUsername("test"); connection.setPassword("test");
            var config = new org.thisway.support.config.RabbitMQConfig(null);
            var converter = config.jackson2JsonMessageConverter();
            template = config.rabbitTemplate(connection, converter);
            admin = new org.springframework.amqp.rabbit.core.RabbitAdmin(connection);
            admin.declareExchange(config.gpsLogExchange());
            admin.declareExchange(config.broadcastExchange());
            admin.declareExchange(config.gpsDeadExchange());
            admin.declareQueue(config.gpsDeadQueue());
            admin.declareBinding(config.gpsDeadBinding());
            // Isolated fixture queue; deployment uses the documented broker DLX policy.
            admin.declareQueue(org.springframework.amqp.core.QueueBuilder.durable(org.thisway.support.config.RabbitMQConfig.GPS_LOG_QUEUE)
                    .deadLetterExchange(org.thisway.support.config.RabbitMQConfig.GPS_LOG_DLX)
                    .deadLetterRoutingKey(org.thisway.support.config.RabbitMQConfig.GPS_LOG_DEAD_KEY).build());
            admin.declareBinding(config.gpsLogBinding());
            admin.declareQueue(new org.springframework.amqp.core.Queue(liveQueue));
            admin.declareBinding(new org.springframework.amqp.core.Binding(liveQueue, org.springframework.amqp.core.Binding.DestinationType.QUEUE,
                    org.thisway.support.config.RabbitMQConfig.BROADCAST_GPS_LOG_EXCHANGE, "", null));
            producer = new org.thisway.vehicle.log.infrastructure.GpsLogProducer(template, converter, mock(io.micrometer.tracing.Tracer.class), meters);
            var consumer = new org.thisway.vehicl_consumer.log.SaveGpsLogConsumer(gpsSave, new io.micrometer.core.instrument.simple.SimpleMeterRegistry(), org.mockito.Mockito.mock(org.thisway.support.logging.GpsCommitLatency.class));
            var endpoint = new org.springframework.amqp.rabbit.config.SimpleRabbitListenerEndpoint();
            endpoint.setId("authenticated-fixture");
            endpoint.setQueueNames(org.thisway.support.config.RabbitMQConfig.GPS_LOG_QUEUE);
            endpoint.setMessageListener(message -> {
                int attempt = attempts.incrementAndGet();
                if (transientFailure && attempt < 3) throw new org.springframework.dao.CannotAcquireLockException("fixture transient");
                consumer.receiveGpsLog((org.thisway.vehicle.log.interfaces.GpsLogRequest) converter.fromMessage(message), message.getMessageProperties().getHeaders());
            });
            container = config.gpsSaveListenerContainerFactory(connection, converter, meters).createListenerContainer(endpoint);
        }

        @Override public void close() {
            producer.close();
            container.stop(); container.destroy();
            admin.deleteQueue(org.thisway.support.config.RabbitMQConfig.GPS_LOG_QUEUE);
            admin.deleteQueue(org.thisway.support.config.RabbitMQConfig.GPS_LOG_DLQ);
            admin.deleteQueue(liveQueue);
            connection.destroy(); meters.close();
        }
    }

    @Test
    @org.junit.jupiter.api.Tag("emulator-client")
    void 실제_Emulator_HTTP요청은_저장되고_키폐기후_거부된다() throws Exception {
        String key = issue(device.getId(), token);
        var credentials = tempDirectory.resolve("device-credentials.json");
        java.nio.file.Files.createFile(credentials, java.nio.file.attribute.PosixFilePermissions
                .asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")));
        java.nio.file.Files.writeString(credentials, json.writeValueAsString(Map.of(device.getMdn(),
                Map.of("device_id", device.getId(), "key", key))));
        try {
            runEmulatorContract(credentials, "accepted");
            for (String kind : List.of("gps", "power", "geofence")) assertThat(logCount(kind)).isEqualTo(1);
            mvc.perform(delete(path(device.getId())).header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
            runEmulatorContract(credentials, "rejected");
            for (String kind : List.of("gps", "power", "geofence")) assertThat(logCount(kind)).isEqualTo(1);
        } finally { java.nio.file.Files.deleteIfExists(credentials); }
    }

    @Test
    @org.junit.jupiter.api.Tag("emulator-client")
    void actualPythonMidnightPacketsPreserveAllFiveMysqlObservationTimes() throws Exception {
        String key = issue(device.getId(), token);
        var credentials = tempDirectory.resolve("boundary-credentials.json");
        java.nio.file.Files.createFile(credentials, java.nio.file.attribute.PosixFilePermissions
                .asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")));
        java.nio.file.Files.writeString(credentials, json.writeValueAsString(Map.of(device.getMdn(),
                Map.of("device_id", device.getId(), "key", key))));
        try {
            runEmulatorContract(credentials, "hour-boundary");
            var times = jdbc.query("SELECT occurred_time FROM gps_log WHERE vehicle_id=? ORDER BY occurred_time",
                    (rs, row) -> rs.getTimestamp(1).toLocalDateTime(), device.getVehicle().getId());
            var first = java.time.LocalDateTime.of(2020, 1, 1, 23, 59, 58);
            assertThat(times).containsExactlyElementsOf(java.util.stream.IntStream.range(0, 5).mapToObj(first::plusSeconds).toList());
        } finally { java.nio.file.Files.deleteIfExists(credentials); }
    }

    private void runEmulatorContract(java.nio.file.Path credentials, String expected) throws Exception {
        var source = java.nio.file.Path.of(System.getProperty("emulator.source")).toAbsolutePath();
        var process = new ProcessBuilder(System.getProperty("emulator.python"), "tests/live_device_contract.py", expected)
                .directory(source.toFile()).redirectErrorStream(true);
        process.environment().put("PYTHONPATH", source.toString());
        process.environment().put("DEVICE_CREDENTIALS_FILE", credentials.toString());
        process.environment().put("TEST_BACKEND_URL", "http://127.0.0.1:" + serverPort);
        process.environment().put("TEST_MDN", device.getMdn());
        var child = process.start();
        try {
            assertThat(child.waitFor(45, java.util.concurrent.TimeUnit.SECONDS)).as("Emulator contract timeout").isTrue();
            assertThat(child.exitValue()).as("Emulator contract failed; no secret output is echoed").isZero();
            assertThat(new String(child.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
                    .isEqualTo(expected.equals("hour-boundary") ? "hour boundary verified\n" : "3 telemetry contracts verified\n");
        } finally { child.destroyForcibly(); }
    }

    @Test
    void duplicateRequestAttemptIsRejectedAcrossEndpointsAndFreshRetryRemainsIdempotent() throws Exception {
        String key = issue(device.getId(), token);
        String nonce = UUID.randomUUID().toString();
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String body = json.writeValueAsString(packet("gps"));
        for (int expected : new int[]{200, 409}) {
            mvc.perform(post("/api/logs/gps").header("X-Device-Id", device.getId())
                    .header("X-Device-Key", key).header("X-Request-Id", nonce).header("X-Request-Timestamp", timestamp)
                    .contentType("application/json").content(body)).andExpect(status().is(expected));
        }
        mvc.perform(post("/api/logs/power").header("X-Device-Id", device.getId())
                .header("X-Device-Key", key).header("X-Request-Id", nonce).header("X-Request-Timestamp", timestamp)
                .contentType("application/json").content(json.writeValueAsString(packet("power")))).andExpect(status().isConflict());
        mvc.perform(post("/api/logs/gps").header("X-Device-Id", device.getId()).header("X-Device-Key", key)
                .header("X-Request-Id", UUID.randomUUID().toString()).header("X-Request-Timestamp", timestamp)
                .contentType("application/json").content(body)).andExpect(status().isOk());
        assertThat(logCount("gps")).isEqualTo(1); assertThat(logCount("power")).isZero();
    }

    @Test
    void missingFreshnessAndRateExceededCannotWrite() throws Exception {
        String key = issue(device.getId(), token);
        String body = json.writeValueAsString(packet("gps"));
        mvc.perform(post("/api/logs/gps").header("X-Device-Id", device.getId()).header("X-Device-Key", key)
                .contentType("application/json").content(body)).andExpect(status().isBadRequest());
        // Exhaust the shared real Redis budget, then verify the HTTP boundary, not just Lua output.
        for (int i = 0; i < 120; i++) requestGuard.accept(device.getId(), UUID.randomUUID().toString(), Long.toString(Instant.now().getEpochSecond()));
        mvc.perform(post("/api/logs/gps").header("X-Device-Id", device.getId()).header("X-Device-Key", key)
                .header("X-Request-Id", UUID.randomUUID().toString()).header("X-Request-Timestamp", Long.toString(Instant.now().getEpochSecond()))
                .contentType("application/json").content(body)).andExpect(status().isTooManyRequests());
        assertThat(logCount("gps")).isZero();
    }

    @Test
    void browserPreflightAllowsAllTelemetryHeadersAndMalformedJsonDoesNotEchoBody() throws Exception {
        mvc.perform(options("/api/logs/gps").header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type,x-device-id,x-device-key,x-request-id,x-request-timestamp"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        var response = mvc.perform(post("/api/logs/gps").contentType("application/json").content("{private-fixture"))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("private-fixture", "Jackson");
    }

    @Test
    void oversizedBrowserRequestStillExposes413ThroughAllowedCors() throws Exception {
        byte[] body = new byte[org.thisway.vehicle.log.interfaces.TelemetryBodyLimitFilter.MAX_BYTES + 1];
        mvc.perform(post("/api/logs/gps").header("Origin", "http://localhost:5173")
                .contentType("application/json").content(body))
                .andExpect(status().is(413)).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mvc.perform(post("/api/logs/gps").header("Origin", "https://unknown.invalid")
                .contentType("application/json").content(body))
                .andExpect(status().isForbidden()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        assertThat(logCount("gps")).isZero();
    }

    private void rejectAdmission(DeviceIdentity identity) {
        assertThatThrownBy(() -> gpsSave.saveGpsLog(gpsPacket(), identity)).isInstanceOf(org.thisway.support.common.CustomException.class);
        assertThatThrownBy(() -> telemetry.streamGps(gpsPacket(), identity)).isInstanceOf(org.thisway.support.common.CustomException.class);
        assertThatThrownBy(() -> telemetry.savePowerLog((org.thisway.vehicle.log.interfaces.PowerLogRequest) packet("power"), identity))
                .isInstanceOf(org.thisway.support.common.CustomException.class);
        assertThatThrownBy(() -> telemetry.saveGeofenceLog((org.thisway.vehicle.log.interfaces.GeofenceLogRequest) packet("geofence"), identity))
                .isInstanceOf(org.thisway.support.common.CustomException.class);
    }

    private int logCount(String kind) {
        // Test-controlled table allowlist; no request input is interpolated.
        if (!List.of("gps", "power", "geofence").contains(kind)) throw new IllegalArgumentException();
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + kind + "_log WHERE vehicle_id=?", Integer.class, device.getVehicle().getId());
    }

    private org.thisway.vehicle.log.interfaces.GpsLogRequest gpsPacket() {
        return new org.thisway.vehicle.log.interfaces.GpsLogRequest(device.getMdn(), "1", "1", "1", "1", "20200101102000", "1",
                List.of(new org.thisway.vehicle.log.interfaces.GpsLogEntry(null, "30", "A", "37000000", "127000000", "0", "0", "100", "12")));
    }

    private Object packet(String kind) {
        return switch (kind) {
            case "gps" -> gpsPacket();
            case "power" -> new org.thisway.vehicle.log.interfaces.PowerLogRequest(device.getMdn(), "1", "1", "1", "1",
                    "20200101102000", "", "A", "37000000", "127000000", "0", "0", "100");
            case "geofence" -> new org.thisway.vehicle.log.interfaces.GeofenceLogRequest(device.getMdn(), "1", "1", "1", "1",
                    "20200101102000", "1", "1", "1", "A", "37000000", "127000000", "0", "0", "100");
            default -> throw new IllegalArgumentException();
        };
    }

    private void rejectAuthentication(long id, String key, String mdn) {
        assertThatThrownBy(() -> authentication.authenticate(id, key, mdn))
                .isInstanceOfSatisfying(org.thisway.support.common.CustomException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(org.thisway.support.common.ErrorCode.DEVICE_AUTHENTICATION_FAILED))
                .hasMessage(org.thisway.support.common.ErrorCode.DEVICE_AUTHENTICATION_FAILED.getMessage());
    }

    private long revision() {
        return jdbc.queryForObject("SELECT assignment_revision FROM emulator WHERE id=?", Long.class, device.getId());
    }

    private void update(Map<String, Object> body) throws Exception {
        mvc.perform(patch("/api/emulators/" + device.getId()).header("Authorization", "Bearer " + token)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    private String issue(long id, String jwt) throws Exception {
        var result = mvc.perform(post(path(id)).header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("key").asText();
    }
    private void expectState(String state) throws Exception {
        mvc.perform(get(path(device.getId())).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value(state));
    }
    private String hash() { return jdbc.queryForObject("SELECT key_hash FROM device_credential WHERE emulator_id=?", String.class, device.getId()); }
    private int events() { return jdbc.queryForObject("SELECT COUNT(*) FROM device_credential_event WHERE emulator_id=?", Integer.class, device.getId()); }
    private String path(long id) { return "/api/emulators/" + id + "/device-key"; }
    private String token(String role) { return tokens.generateAccessToken(admin.getEmail(), Map.of("roles", List.of(role), "companyId", company.getId())); }
    private Company company() {
        return companies.save(Company.builder().name("fixture").crn(UUID.randomUUID().toString()).contact("000")
                .addrRoad("fixture").addrDetail("fixture").memo("fixture").gpsCycle(60).build());
    }
    private Emulator device(Company owner) {
        var model = models.save(VehicleModel.builder().name("fixture").manufacturer("fixture").modelYear(2020).build());
        var vehicle = vehicles.save(Vehicle.builder().company(owner).vehicleModel(model).carNumber(UUID.randomUUID().toString())
                .color("white").mileage(0).powerOn(false).build());
        return emulators.save(Emulator.builder().mdn(UUID.randomUUID().toString().substring(0,20)).vehicle(vehicle)
                .terminalId("fixture").manufactureId(1).packetVersion(1).deviceId(1).deviceFirmwareVersion("1").build());
    }
}
