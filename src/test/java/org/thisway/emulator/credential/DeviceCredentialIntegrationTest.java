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
@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.batch.jdbc.initialize-schema=never"})
@AutoConfigureMockMvc
@DirtiesContext
class DeviceCredentialIntegrationTest {
    @org.testcontainers.junit.jupiter.Container
    static final org.testcontainers.containers.GenericContainer<?> MYSQL =
            new org.testcontainers.containers.GenericContainer<>("mysql:8.0.40")
                    .withEnv("MYSQL_DATABASE", "credentials_test").withEnv("MYSQL_USER", "test")
                    .withEnv("MYSQL_PASSWORD", "test").withEnv("MYSQL_ROOT_PASSWORD", "test-root")
                    .withExposedPorts(3306).waitingFor(org.testcontainers.containers.wait.strategy.Wait
                            .forLogMessage(".*ready for connections.*port: 3306.*", 1));
    @org.springframework.test.context.DynamicPropertySource
    static void database(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:mysql://" + MYSQL.getHost() + ":"
                + MYSQL.getMappedPort(3306) + "/credentials_test?allowPublicKeyRetrieval=true&useSSL=false");
        registry.add("spring.datasource.username", () -> "test");
        registry.add("spring.datasource.password", () -> "test");
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenProvider tokens;
    @Autowired CompanyRepository companies;
    @Autowired VehicleModelRepository models;
    @Autowired VehicleRepository vehicles;
    @Autowired MemberRepository members;
    @Autowired EmulatorRepository emulators;
    @MockitoSpyBean DeviceCredentialRepository repository;
    private Company company;
    private Member admin;
    private Emulator device;
    private String token;

    @BeforeEach
    void fixture() {
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
        var binding = new DeviceCredentialRepository.Binding(other.getId(), other.getVehicle().getId(), company.getId(), other.getMdn());
        assertThatThrownBy(() -> repository.replace(binding, before, Instant.now(), Instant.now().plusSeconds(3600)))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        assertThat(hash()).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM device_credential WHERE emulator_id=?", Integer.class, other.getId())).isZero();
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
