package org.thisway.vehicle.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class VehicleTenantIntegrationTest {

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

    private Company companyA;
    private Company companyB;
    private VehicleModel vehicleModel;
    private Vehicle companyBVehicle;
    private String companyAToken;

    @BeforeEach
    void setUp() {
        vehicleRepository.deleteAll();
        vehicleModelRepository.deleteAll();
        memberRepository.deleteAll();
        companyRepository.deleteAll();

        companyA = companyRepository.save(company("company-a", "crn-a"));
        companyB = companyRepository.save(company("company-b", "crn-b"));
        memberRepository.save(member(companyA, "admin-a@example.com"));
        vehicleModel = vehicleModelRepository.save(VehicleModel.builder()
                .manufacturer("Hyundai")
                .modelYear(2026)
                .name("Ioniq")
                .build());
        companyBVehicle = vehicleRepository.save(vehicle(companyB, "22나2222"));
        companyAToken = accessToken("admin-a@example.com", companyA.getId());
    }

    @Test
    void repository는_vehicleId와_companyId를_함께_만족할_때만_반환한다() {
        assertThat(vehicleRepository.findByIdAndCompanyIdAndActiveTrue(
                companyBVehicle.getId(), companyB.getId()))
                .contains(companyBVehicle);
        assertThat(vehicleRepository.findByIdAndCompanyIdAndActiveTrue(
                companyBVehicle.getId(), companyA.getId()))
                .isEmpty();
    }

    @Test
    void 자기_업체_차량은_상세_조회할_수_있다() throws Exception {
        Vehicle companyAVehicle = vehicleRepository.save(vehicle(companyA, "11가1111"));

        mockMvc.perform(get("/api/vehicles/{id}", companyAVehicle.getId())
                        .header(AUTHORIZATION, bearer(companyAToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(companyAVehicle.getId()));
    }

    @Test
    void 다른_업체_차량_상세_조회는_404를_반환한다() throws Exception {
        assertCrossTenantNotFound(get("/api/vehicles/{id}", companyBVehicle.getId()));
    }

    @Test
    void 다른_업체_차량_수정은_404이고_데이터를_바꾸지_않는다() throws Exception {
        mockMvc.perform(patch("/api/vehicles/{id}", companyBVehicle.getId())
                        .header(AUTHORIZATION, bearer(companyAToken))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "carNumber": "99하9999",
                                  "color": "red"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.VEHICLE_NOT_FOUND.getCode()));

        Vehicle unchangedVehicle = vehicleRepository.findById(companyBVehicle.getId()).orElseThrow();
        assertThat(unchangedVehicle.getCarNumber()).isEqualTo("22나2222");
        assertThat(unchangedVehicle.getColor()).isEqualTo("white");
    }

    @Test
    void 다른_업체_차량_삭제는_404이고_active를_유지한다() throws Exception {
        assertCrossTenantNotFound(delete("/api/vehicles/{id}", companyBVehicle.getId()));

        assertThat(vehicleRepository.findById(companyBVehicle.getId()).orElseThrow().isActive()).isTrue();
    }

    private void assertCrossTenantNotFound(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request
    ) throws Exception {
        mockMvc.perform(request.header(AUTHORIZATION, bearer(companyAToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.VEHICLE_NOT_FOUND.getCode()));
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

    private Vehicle vehicle(Company company, String carNumber) {
        return Vehicle.builder()
                .company(company)
                .vehicleModel(vehicleModel)
                .carNumber(carNumber)
                .color("white")
                .mileage(0)
                .powerOn(false)
                .build();
    }
}
