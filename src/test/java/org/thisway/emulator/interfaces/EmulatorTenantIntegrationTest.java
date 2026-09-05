package org.thisway.emulator.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.company.domain.Company;
import org.thisway.company.infrastructure.CompanyRepository;
import org.thisway.emulator.domain.Emulator;
import org.thisway.emulator.infrastructure.EmulatorRepository;
import org.thisway.member.domain.Member;
import org.thisway.member.domain.MemberRole;
import org.thisway.member.infrastructure.MemberRepository;
import org.thisway.support.common.ErrorCode;
import org.thisway.support.security.utils.JwtTokenProvider;
import org.thisway.vehicle.domain.Vehicle;
import org.thisway.vehicle.infrastructure.VehicleRepository;
import org.thisway.vehicle.vehicle_model.domain.VehicleModel;
import org.thisway.vehicle.vehicle_model.infrastructure.VehicleModelRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EmulatorTenantIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private VehicleModelRepository vehicleModelRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private EmulatorRepository emulatorRepository;

    private Company companyA;
    private Company companyB;
    private Vehicle companyAVehicle;
    private Vehicle companyBVehicle;
    private Emulator companyBEmulator;
    private String companyAToken;

    @BeforeEach
    void setUp() {
        emulatorRepository.deleteAll();
        vehicleRepository.deleteAll();
        vehicleModelRepository.deleteAll();
        memberRepository.deleteAll();
        companyRepository.deleteAll();

        companyA = companyRepository.save(company("company-a", "crn-a"));
        companyB = companyRepository.save(company("company-b", "crn-b"));
        memberRepository.save(member(companyA, "admin-a@example.com"));
        VehicleModel model = vehicleModelRepository.save(VehicleModel.builder()
                .manufacturer("Hyundai")
                .modelYear(2026)
                .name("Ioniq")
                .build());
        companyAVehicle = vehicleRepository.save(vehicle(companyA, model, "11가1111"));
        companyBVehicle = vehicleRepository.save(vehicle(companyB, model, "22나2222"));
        companyBEmulator = emulatorRepository.save(emulator(companyBVehicle, "01000000002"));
        companyAToken = accessToken("admin-a@example.com", companyA.getId());
    }

    @Test
    void repository는_emulatorId와_companyId를_함께_만족할_때만_반환한다() {
        assertThat(emulatorRepository.findByIdAndVehicleCompanyId(
                companyBEmulator.getId(), companyB.getId()))
                .contains(companyBEmulator);
        assertThat(emulatorRepository.findByIdAndVehicleCompanyId(
                companyBEmulator.getId(), companyA.getId()))
                .isEmpty();
    }

    @Test
    void 목록은_자기_업체_에뮬레이터만_반환한다() throws Exception {
        Emulator companyAEmulator = emulatorRepository.save(emulator(companyAVehicle, "01000000001"));

        mockMvc.perform(get("/api/emulators")
                        .header(AUTHORIZATION, bearer(companyAToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emulators.length()").value(1))
                .andExpect(jsonPath("$.emulators[0].id").value(companyAEmulator.getId()));
    }

    @Test
    void 다른_업체_에뮬레이터_상세는_404를_반환한다() throws Exception {
        assertEmulatorNotFound(get("/api/emulators/{id}", companyBEmulator.getId()));
    }

    @Test
    void 다른_업체_에뮬레이터_수정은_404이고_데이터를_바꾸지_않는다() throws Exception {
        mockMvc.perform(patch("/api/emulators/{id}", companyBEmulator.getId())
                        .header(AUTHORIZATION, bearer(companyAToken))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "terminalId": "attacker-updated-terminal"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.EMULATOR_NOT_FOUND.getCode()));

        assertThat(emulatorRepository.findById(companyBEmulator.getId()).orElseThrow().getTerminalId())
                .isEqualTo("terminal-id");
    }

    @Test
    void 다른_업체_에뮬레이터_삭제는_404이고_row를_유지한다() throws Exception {
        assertEmulatorNotFound(delete("/api/emulators/{id}", companyBEmulator.getId()));

        assertThat(emulatorRepository.existsById(companyBEmulator.getId())).isTrue();
    }

    @Test
    void 다른_업체_차량에는_에뮬레이터를_등록할_수_없다() throws Exception {
        mockMvc.perform(post("/api/emulators")
                        .header(AUTHORIZATION, bearer(companyAToken))
                        .contentType(APPLICATION_JSON)
                        .content(registerBody(companyBVehicle.getId(), "01000000003")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.VEHICLE_NOT_FOUND.getCode()));

        assertThat(emulatorRepository.findByMdn("01000000003")).isEmpty();
    }

    @Test
    void 자기_에뮬레이터를_다른_업체_차량으로_옮길_수_없다() throws Exception {
        Emulator companyAEmulator = emulatorRepository.save(emulator(companyAVehicle, "01000000001"));

        mockMvc.perform(patch("/api/emulators/{id}", companyAEmulator.getId())
                        .header(AUTHORIZATION, bearer(companyAToken))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "vehicleId": %d
                                }
                                """.formatted(companyBVehicle.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.VEHICLE_NOT_FOUND.getCode()));

        assertThat(emulatorRepository.findById(companyAEmulator.getId()).orElseThrow().getVehicle().getId())
                .isEqualTo(companyAVehicle.getId());
    }

    private void assertEmulatorNotFound(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request
    ) throws Exception {
        mockMvc.perform(request.header(AUTHORIZATION, bearer(companyAToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.EMULATOR_NOT_FOUND.getCode()));
    }

    private String registerBody(long vehicleId, String mdn) {
        return """
                {
                  "mdn": "%s",
                  "vehicleId": %d,
                  "terminalId": "terminal-id",
                  "manufactureId": 1,
                  "packetVersion": 1,
                  "deviceId": 1,
                  "deviceFirmwareVersion": "1.0.0"
                }
                """.formatted(mdn, vehicleId);
    }

    private String accessToken(String email, long companyId) {
        return jwtTokenProvider.generateAccessToken(email, Map.of(
                "roles", List.of(MemberRole.COMPANY_ADMIN.name()),
                "companyId", companyId
        ));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private Company company(String name, String crn) {
        return Company.builder()
                .name(name)
                .crn(crn)
                .contact("01000000000")
                .addrRoad("road")
                .addrDetail("detail")
                .memo("memo")
                .gpsCycle(60)
                .build();
    }

    private Member member(Company company, String email) {
        return Member.builder()
                .company(company)
                .role(MemberRole.COMPANY_ADMIN)
                .name("company-admin")
                .email(email)
                .password("Password123!")
                .phone("01012345678")
                .memo("memo")
                .build();
    }

    private Vehicle vehicle(Company company, VehicleModel vehicleModel, String carNumber) {
        return Vehicle.builder()
                .company(company)
                .vehicleModel(vehicleModel)
                .carNumber(carNumber)
                .color("white")
                .mileage(0)
                .powerOn(false)
                .build();
    }

    private Emulator emulator(Vehicle vehicle, String mdn) {
        return Emulator.builder()
                .mdn(mdn)
                .vehicle(vehicle)
                .terminalId("terminal-id")
                .manufactureId(1)
                .packetVersion(1)
                .deviceId(1)
                .deviceFirmwareVersion("1.0.0")
                .build();
    }
}
