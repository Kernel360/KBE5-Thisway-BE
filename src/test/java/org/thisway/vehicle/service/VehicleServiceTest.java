package org.thisway.vehicle.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;
import org.thisway.company.entity.Company;
import org.thisway.company.repository.CompanyRepository;
import org.thisway.member.entity.Member;
import org.thisway.member.entity.MemberRole;
import org.thisway.security.dto.request.MemberDetails;
import org.thisway.security.service.SecurityService;
import org.thisway.vehicle.dto.request.VehicleCreateRequest;
import org.thisway.vehicle.dto.request.VehicleUpdateRequest;
import org.thisway.vehicle.dto.response.VehicleDashboardResponse;
import org.thisway.vehicle.dto.response.VehicleResponse;
import org.thisway.vehicle.dto.response.VehiclesResponse;
import org.thisway.vehicle.entity.Vehicle;
import org.thisway.vehicle.entity.VehicleModel;
import org.thisway.vehicle.repository.VehicleModelRepository;
import org.thisway.vehicle.repository.VehicleRepository;
import org.thisway.vehicle.validation.VehicleUpdateValidator;

@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private VehicleModelRepository vehicleModelRepository;

    @Mock
    private VehicleUpdateValidator vehicleUpdateValidator;

    @Mock
    private SecurityService securityService;

    @InjectMocks
    private VehicleService vehicleService;

    @Captor
    private ArgumentCaptor<VehicleModel> vehicleModelCaptor;

    @Captor
    private ArgumentCaptor<Vehicle> vehicleCaptor;

    @Captor
    private ArgumentCaptor<Long> vehicleIdCaptor;

    @Test
    @DisplayName("차량 등록이 성공하는 경우")
    void 차량_등록_성공() {
        // given
        Long vehicleModelId = 1L;
        VehicleCreateRequest request = new VehicleCreateRequest(
                vehicleModelId,
                "12가3456",
                "검정"
        );

        Company mockCompany = mock(Company.class);
        when(mockCompany.getId()).thenReturn(1L);
        when(mockCompany.isActive()).thenReturn(true);

        MemberRole mockRole = mock(MemberRole.class);
        when(mockRole.getLevel()).thenReturn(MemberRole.COMPANY_ADMIN.getLevel());

        Member mockMember = mock(Member.class);
        when(mockMember.getCompany()).thenReturn(mockCompany);
        when(mockMember.getRole()).thenReturn(mockRole);

        VehicleModel existingVehicleModel = VehicleModel.builder()
                .manufacturer("현대")
                .modelYear(2023)
                .name("아반떼")
                .build();

        mockSecurityContext(mockMember);

        when(companyRepository.findById(1L)).thenReturn(Optional.of(mockCompany));
        when(vehicleModelRepository.findByIdAndActiveTrue(vehicleModelId)).thenReturn(
                Optional.of(existingVehicleModel));

        // when
        vehicleService.registerVehicle(request);

        // then
        verify(vehicleModelRepository).findByIdAndActiveTrue(vehicleModelId);
        verify(companyRepository).findById(1L);
        verify(vehicleRepository).save(vehicleCaptor.capture());

        Vehicle capturedVehicle = vehicleCaptor.getValue();

        assertEquals("12가3456", capturedVehicle.getCarNumber());
        assertEquals("검정", capturedVehicle.getColor());
        assertEquals(existingVehicleModel, capturedVehicle.getVehicleModel());
    }

    @Test
    @DisplayName("차량 등록시 회사를 찾을 수 없는 경우 차량 등록 불가")
    void 차량_등록_실패_회사를_찾을수없음() {
        // given
        Long vehicleModelId = 1L;
        VehicleCreateRequest request = new VehicleCreateRequest(
                vehicleModelId,
                "12가3456",
                "검정"
        );

        Company mockCompany = mock(Company.class);
        when(mockCompany.getId()).thenReturn(1L);

        MemberRole mockRole = mock(MemberRole.class);
        when(mockRole.getLevel()).thenReturn(MemberRole.COMPANY_ADMIN.getLevel());

        Member mockMember = mock(Member.class);
        when(mockMember.getCompany()).thenReturn(mockCompany);
        when(mockMember.getRole()).thenReturn(mockRole);

        mockSecurityContext(mockMember);

        when(companyRepository.findById(1L)).thenReturn(Optional.empty());

        // when & then
        CustomException exception = assertThrows(CustomException.class,
                () -> vehicleService.registerVehicle(request));

        verify(companyRepository).findById(1L);
        verify(vehicleModelRepository, never()).findByIdAndActiveTrue(any());
        verify(vehicleRepository, never()).save(any(Vehicle.class));

        assertEquals(ErrorCode.COMPANY_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("차량 등록시 차량 모델을 찾을 수 없는 경우 차량 등록 불가")
    void 차량_등록_실패_차량모델을_찾을수없음() {
        // given
        Long vehicleModelId = 1L;
        VehicleCreateRequest request = new VehicleCreateRequest(
                vehicleModelId,
                "12가3456",
                "검정"
        );

        Company mockCompany = mock(Company.class);
        when(mockCompany.getId()).thenReturn(1L);
        when(mockCompany.isActive()).thenReturn(true);

        MemberRole mockRole = mock(MemberRole.class);
        when(mockRole.getLevel()).thenReturn(MemberRole.COMPANY_ADMIN.getLevel());

        Member mockMember = mock(Member.class);
        when(mockMember.getCompany()).thenReturn(mockCompany);
        when(mockMember.getRole()).thenReturn(mockRole);

        mockSecurityContext(mockMember);

        when(companyRepository.findById(1L)).thenReturn(Optional.of(mockCompany));
        when(vehicleModelRepository.findByIdAndActiveTrue(vehicleModelId)).thenReturn(Optional.empty());

        // when & then
        CustomException exception = assertThrows(CustomException.class,
                () -> vehicleService.registerVehicle(request));

        verify(companyRepository).findById(1L);
        verify(vehicleModelRepository).findByIdAndActiveTrue(vehicleModelId);
        verify(vehicleRepository, never()).save(any(Vehicle.class));

        assertEquals(ErrorCode.VEHICLE_MODEL_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("차량 상세 조회 성공")
    void 차량_조회_성공() {
        // given
        Long vehicleId = 1L;
        Company company = mock(Company.class);

        MemberRole mockRole = mock(MemberRole.class);
        Set<MemberRole> roles = new HashSet<>();
        roles.add(MemberRole.COMPANY_ADMIN);
        when(mockRole.getLowerOrEqualRoles()).thenReturn(roles);

        Member mockMember = mock(Member.class);
        when(mockMember.getCompany()).thenReturn(company);
        when(mockMember.getRole()).thenReturn(mockRole);

        mockSecurityContext(mockMember);

        VehicleModel vehicleModel = VehicleModel.builder()
                .manufacturer("현대")
                .modelYear(2023)
                .name("쏘나타")
                .build();
        Vehicle vehicle = Vehicle.builder()
                .company(company)
                .vehicleModel(vehicleModel)
                .carNumber("12가3456")
                .mileage(50000)
                .build();

        when(vehicleRepository.findByIdAndActiveTrue(vehicleId)).thenReturn(Optional.of(vehicle));

        // when
        VehicleResponse response = vehicleService.getVehicleDetail(vehicleId);

        // then
        verify(vehicleRepository).findByIdAndActiveTrue(vehicleIdCaptor.capture());
        assertThat(vehicleIdCaptor.getValue()).isEqualTo(vehicleId);

        assertThat(response).isNotNull();
        assertThat(response.manufacturer()).isEqualTo("현대");
        assertThat(response.modelYear()).isEqualTo(2023);
        assertThat(response.model()).isEqualTo("쏘나타");
        assertThat(response.carNumber()).isEqualTo("12가3456");
        assertThat(response.mileage()).isEqualTo(50000);
    }

    @Test
    @DisplayName("차량 목록 조회 성공 - 기본 페이지네이션")
    void 차량_목록_조회_성공_기본_페이지네이션() {
        // given
        PageRequest pageRequest = PageRequest.of(0, 10);

        Company mockCompany = mock(Company.class);

        Member mockMember = mock(Member.class);
        when(mockMember.getCompany()).thenReturn(mockCompany);

        mockSecurityContext(mockMember);

        List<Vehicle> vehicles = List.of(
                createMockVehicle("현대", "아반떼", "12가3456"),
                createMockVehicle("기아", "K5", "34나5678")
        );
        Page<Vehicle> mockPage = new PageImpl<>(vehicles);

        when(vehicleRepository.findAllByCompanyAndActiveTrue(eq(mockCompany), eq(pageRequest))).thenReturn(mockPage);

        // when
        VehiclesResponse response = vehicleService.getVehicles(pageRequest);

        // then
        verify(vehicleRepository).findAllByCompanyAndActiveTrue(eq(mockCompany), eq(pageRequest));
        assertEquals(2, response.vehicles().size());
        assertEquals(1, response.totalPages());
        assertEquals(2L, response.totalElements());
        assertEquals(0, response.currentPage());
        assertEquals(2, response.size());
    }

    @Test
    @DisplayName("차량 정보 수정 성공")
    void 차량_정보_수정_성공() {
        // given
        Long vehicleId = 1L;
        VehicleUpdateRequest request = new VehicleUpdateRequest(
                "34가5678",
                "흰색",
                null,
                2024,
                "K5"
        );

        Company mockCompany = mock(Company.class);
        when(mockCompany.getId()).thenReturn(1L);

        MemberRole mockRole = mock(MemberRole.class);
        Set<MemberRole> roles = new HashSet<>();
        roles.add(MemberRole.COMPANY_ADMIN);
        when(mockRole.getLowerOrEqualRoles()).thenReturn(roles);

        Member mockMember = mock(Member.class);
        when(mockMember.getCompany()).thenReturn(mockCompany);
        when(mockMember.getRole()).thenReturn(mockRole);

        mockSecurityContext(mockMember);

        VehicleModel vehicleModel = VehicleModel.builder()
                .manufacturer("현대")
                .modelYear(2023)
                .name("아반떼")
                .build();

        Vehicle vehicle = Vehicle.builder()
                .vehicleModel(vehicleModel)
                .carNumber("12가3456")
                .color("검정")
                .mileage(5000)
                .company(mockCompany)
                .build();

        when(vehicleRepository.findByIdAndActiveTrue(vehicleId)).thenReturn(Optional.of(vehicle));
        doNothing().when(vehicleUpdateValidator).validateUpdateRequest(vehicle, request);

        // when
        vehicleService.updateVehicle(vehicleId, request);

        // then
        verify(vehicleRepository).findByIdAndActiveTrue(vehicleId);
        verify(vehicleUpdateValidator).validateUpdateRequest(vehicle, request);

        assertEquals("34가5678", vehicle.getCarNumber());
        assertEquals("흰색", vehicle.getColor());
        assertEquals("현대", vehicle.getVehicleModel().getManufacturer());
        assertEquals(2024, vehicle.getVehicleModel().getModelYear());
        assertEquals("K5", vehicle.getVehicleModel().getName());
    }

    @Test
    @DisplayName("차량 대시보드 조회 성공")
    void 차량_대시보드_조회_성공() {
        // given
        long memberCompanyId = 1L;
        MemberDetails memberDetails = MemberDetails.builder()
                .companyId(memberCompanyId)
                .build();

        given(securityService.getCurrentMemberDetails()).willReturn(memberDetails);
        given(vehicleRepository.countByCompanyIdAndActiveTrue(memberCompanyId)).willReturn(3L);
        given(vehicleRepository.countByCompanyIdAndPowerOnIsAndActiveTrue(memberCompanyId, true)).willReturn(2L);
        given(vehicleRepository.countByCompanyIdAndPowerOnIsAndActiveTrue(memberCompanyId, false)).willReturn(1L);

        // when
        VehicleDashboardResponse response = vehicleService.getVehicleDashboard();

        // then
        assertThat(response.totalVehicles()).isEqualTo(3L);
        assertThat(response.powerOnVehicles()).isEqualTo(2L);
        assertThat(response.powerOffVehicles()).isEqualTo(1L);
    }

    private Vehicle createMockVehicle(String manufacturer, String model, String carNumber) {
        VehicleModel vehicleModel = mock(VehicleModel.class);
        given(vehicleModel.getManufacturer()).willReturn(manufacturer);
        given(vehicleModel.getName()).willReturn(model);
        given(vehicleModel.getModelYear()).willReturn(2023);

        Company company = mock(Company.class);

        Vehicle vehicle = mock(Vehicle.class);
        given(vehicle.getId()).willReturn(1L);
        given(vehicle.getVehicleModel()).willReturn(vehicleModel);
        given(vehicle.getCarNumber()).willReturn(carNumber);
        given(vehicle.getColor()).willReturn("검정");
        given(vehicle.getMileage()).willReturn(5000);
        given(vehicle.isPowerOn()).willReturn(false);

        return vehicle;
    }

    /**
     * SecurityContextHolder 모킹
     */
    private void mockSecurityContext(Member member) {
        when(securityService.getCurrentMember()).thenReturn(member);
    }
}
