package org.thisway.vehicle.triplog.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.thisway.support.component.streaming.SseConnection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
import org.thisway.vehicle.log.application.LogService;
import org.thisway.vehicle.triplog.domain.TripLog;
import org.thisway.vehicle.triplog.infrastructure.TripLogRepository;
import org.thisway.vehicle.vehicle_model.domain.VehicleModel;
import org.thisway.vehicle.vehicle_model.infrastructure.VehicleModelRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TripLogTenantIntegrationTest {

    private static final LocalDateTime START_TIME = LocalDateTime.of(2026, 9, 5, 9, 0);
    private static final LocalDateTime END_TIME = START_TIME.plusHours(1);

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
    private TripLogRepository tripLogRepository;

    @MockitoBean
    private LogService logService;

    private Company companyA;
    private Company companyB;
    private Vehicle companyBVehicle;
    private TripLog companyBTrip;
    private String companyAToken;

    @Autowired
    private SseConnection connections;

    @AfterEach
    void closeStreams() {
        for (String key : List.copyOf(connections.getAllKeys())) {
            connections.get(key).ifPresent(emitter -> emitter.complete());
            connections.remove(key);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/trip-log/current/stream/1", "/api/trip-log/detail/stream/1", "/api/vehicles/stream/track"})
    void query_token만으로_SSE에_접근할_수_없다(String path) throws Exception {
        mockMvc.perform(get(path).param("token", companyAToken))
                .andExpect(status().isUnauthorized());
        assertThat(connections.getAllKeys()).isEmpty();
        verifyNoInteractions(logService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/trip-log/current/stream/1", "/api/trip-log/detail/stream/1", "/api/vehicles/stream/track"})
    void 변조된_헤더_토큰은_SSE에서_401이다(String path) throws Exception {
        mockMvc.perform(get(path).header(AUTHORIZATION, "Bearer invalid.token.signature"))
                .andExpect(status().isUnauthorized());
        assertThat(connections.getAllKeys()).isEmpty();
        verifyNoInteractions(logService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/trip-log/current/stream/1", "/api/trip-log/detail/stream/1", "/api/vehicles/stream/track"})
    void 허용되지_않은_ADMIN_role은_SSE에서_403이다(String path) throws Exception {
        String token = jwtTokenProvider.generateAccessToken("admin@example.com", Map.of(
                "roles", List.of("ADMIN"), "companyId", companyA.getId()));
        mockMvc.perform(get(path).header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());
        assertThat(connections.getAllKeys()).isEmpty();
    }

    @Test
    void 다른_회사_차량_SSE는_연결_생성_전에_404이다() throws Exception {
        mockMvc.perform(get("/api/trip-log/current/stream/{id}", companyBVehicle.getId())
                        .header(AUTHORIZATION, bearer(companyAToken)))
                .andExpect(status().isNotFound());
        assertThat(connections.getAllKeys()).isEmpty();
        verifyNoInteractions(logService);
    }

    @Test
    void 다른_회사_운행_SSE는_GPS조회_전에_404이다() throws Exception {
        mockMvc.perform(get("/api/trip-log/detail/stream/{id}", companyBTrip.getId())
                        .header(AUTHORIZATION, bearer(companyAToken)))
                .andExpect(status().isNotFound());
        verifyNoInteractions(logService);
    }

    @Test
    void MEMBER는_자기_회사_차량_SSE를_구독한다() throws Exception {
        Vehicle owned = vehicleRepository.save(vehicle(companyA,
                vehicleModelRepository.findAll().getFirst(), "11가1111", false));
        String token = jwtTokenProvider.generateAccessToken("member@example.com", Map.of(
                "roles", List.of("MEMBER"), "companyId", companyA.getId()));
        mockMvc.perform(get("/api/trip-log/current/stream/{id}", owned.getId())
                        .header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/trip-log/current/stream/{id}", owned.getId())
                        .header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
        assertThat(connections.getAllKeys()).hasSize(2).allSatisfy(key ->
                assertThat(key).startsWith("vehicle:" + owned.getId() + ":member@example.com:"));
    }

    @Test
    void 자기_회사_운행_SSE는_헤더_인증으로_성공한다() throws Exception {
        Vehicle owned = vehicleRepository.save(vehicle(companyA,
                vehicleModelRepository.findAll().getFirst(), "11가1111", false));
        TripLog ownedTrip = tripLogRepository.save(trip(owned));
        mockMvc.perform(get("/api/trip-log/detail/stream/{id}", ownedTrip.getId())
                        .header(AUTHORIZATION, bearer(companyAToken)))
                .andExpect(status().isOk());
    }

    @Test
    void 회사_SSE는_요청_companyId가_아닌_principal_회사를_구독한다() throws Exception {
        mockMvc.perform(get("/api/vehicles/stream/track")
                        .param("companyId", companyB.getId().toString())
                        .header(AUTHORIZATION, bearer(companyAToken)))
                .andExpect(status().isOk());
        assertThat(connections.getAllKeys()).hasSize(1).allSatisfy(key ->
                assertThat(key).startsWith("company:" + companyA.getId() + ":admin-a@example.com:"));
    }

    @BeforeEach
    void setUp() {
        tripLogRepository.deleteAll();
        vehicleRepository.deleteAll();
        vehicleModelRepository.deleteAll();
        memberRepository.deleteAll();
        companyRepository.deleteAll();

        companyA = companyRepository.save(company("company-a", "crn-a"));
        companyB = companyRepository.save(company("company-b", "crn-b"));
        memberRepository.save(member(companyA, "admin-a@example.com"));
        VehicleModel vehicleModel = vehicleModelRepository.save(VehicleModel.builder()
                .manufacturer("Hyundai")
                .modelYear(2026)
                .name("Ioniq")
                .build());
        companyBVehicle = vehicleRepository.save(vehicle(companyB, vehicleModel, "22나2222", true));
        companyBTrip = tripLogRepository.save(trip(companyBVehicle));
        companyAToken = accessToken("admin-a@example.com", companyA.getId());
    }

    @Test
    void repository는_tripId와_companyId를_함께_만족할_때만_반환한다() {
        assertThat(tripLogRepository.findByIdAndVehicleCompanyIdAndActiveTrue(
                companyBTrip.getId(), companyB.getId()))
                .contains(companyBTrip);
        assertThat(tripLogRepository.findByIdAndVehicleCompanyIdAndActiveTrue(
                companyBTrip.getId(), companyA.getId()))
                .isEmpty();
    }

    @Test
    void 자기_업체_운행_상세는_조회할_수_있다() throws Exception {
        VehicleModel vehicleModel = vehicleModelRepository.findAll().getFirst();
        Vehicle companyAVehicle = vehicleRepository.save(vehicle(companyA, vehicleModel, "11가1111", false));
        TripLog companyATrip = tripLogRepository.save(trip(companyAVehicle));

        mockMvc.perform(get("/api/trip-log/detail/{id}", companyATrip.getId())
                        .header(AUTHORIZATION, bearer(companyAToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.carNumber").value("11가1111"));
    }

    @Test
    void 다른_업체_차량의_운행_요약은_404를_반환한다() throws Exception {
        assertVehicleNotFound(get("/api/trip-log/{id}", companyBVehicle.getId()));
        verifyNoInteractions(logService);
    }

    @Test
    void 다른_업체_차량의_현재_GPS는_404를_반환한다() throws Exception {
        assertVehicleNotFound(get("/api/trip-log/current/{id}", companyBVehicle.getId())
                .param("time", START_TIME.toString()));
        verifyNoInteractions(logService);
    }

    @Test
    void 다른_업체_운행_상세는_404를_반환한다() throws Exception {
        mockMvc.perform(get("/api/trip-log/detail/{id}", companyBTrip.getId())
                        .header(AUTHORIZATION, bearer(companyAToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.TRIP_LOG_NOT_FOUND.getCode()));
        verifyNoInteractions(logService);
    }

    private void assertVehicleNotFound(
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

    private Vehicle vehicle(Company company, VehicleModel vehicleModel, String carNumber, boolean powerOn) {
        return Vehicle.builder()
                .company(company)
                .vehicleModel(vehicleModel)
                .carNumber(carNumber)
                .color("white")
                .mileage(0)
                .powerOn(powerOn)
                .build();
    }

    private TripLog trip(Vehicle vehicle) {
        return TripLog.builder()
                .vehicle(vehicle)
                .startTime(START_TIME)
                .endTime(END_TIME)
                .totalTripMeter(1000)
                .onAddress("start")
                .onAddrDetail("detail")
                .offAddress("end")
                .offAddrDetail("detail")
                .active(true)
                .build();
    }
}
