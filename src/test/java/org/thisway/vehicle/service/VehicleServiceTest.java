package org.thisway.vehicle.service;

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
import org.springframework.data.domain.Sort;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;
import org.thisway.company.entity.Company;
import org.thisway.company.repository.CompanyRepository;
import org.thisway.vehicle.dto.request.VehicleCreateRequest;
import org.thisway.vehicle.dto.request.VehicleUpdateRequest;
import org.thisway.vehicle.dto.response.VehicleResponse;
import org.thisway.vehicle.dto.response.VehiclesResponse;
import org.thisway.vehicle.entity.Vehicle;
import org.thisway.vehicle.entity.VehicleDetail;
import org.thisway.vehicle.repository.VehicleDetailRepository;
import org.thisway.vehicle.repository.VehicleRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private VehicleDetailRepository vehicleDetailRepository;

    @InjectMocks
    private VehicleService vehicleService;

    @Captor
    private ArgumentCaptor<VehicleDetail> vehicleDetailCaptor;

    @Captor
    private ArgumentCaptor<Vehicle> vehicleCaptor;

    @Captor
    private ArgumentCaptor<Long> vehicleIdCaptor;

    @Test
    @DisplayName("차량 등록이 성공하는 경우")
    void 차량_등록_성공() {
        // given
        VehicleCreateRequest request = new VehicleCreateRequest(
                "현대",
                2023,
                "아반떼",
                "12가3456",
                "검정",
                5000,
                37.5665,
                126.9780
        );

        Company mockCompany = mock(Company.class);
        when(mockCompany.isActive()).thenReturn(true);
        given(companyRepository.findById(1L)).willReturn(Optional.of(mockCompany));
        given(vehicleDetailRepository.save(any(VehicleDetail.class))).willReturn(mock(VehicleDetail.class));

        // when
        vehicleService.registerVehicle(request);

        // then
        verify(vehicleDetailRepository).save(vehicleDetailCaptor.capture());
        verify(companyRepository).findById(1L);
        verify(vehicleRepository).save(vehicleCaptor.capture());

        VehicleDetail capturedDetail = vehicleDetailCaptor.getValue();
        Vehicle capturedVehicle = vehicleCaptor.getValue();

        assertEquals("현대", capturedDetail.getManufacturer());
        assertEquals("아반떼", capturedDetail.getModel());
        assertEquals(2023, capturedDetail.getModelYear());

        assertEquals("12가3456", capturedVehicle.getCarNumber());
        assertEquals("검정", capturedVehicle.getColor());
        assertEquals(5000, capturedVehicle.getMileage());
        assertEquals(37.5665, capturedVehicle.getLatitude());
        assertEquals(126.9780, capturedVehicle.getLongitude());
    }

    @Test
    @DisplayName("차량 등록시 회사를 찾을 수 없는 경우 차량 등록 불가")
    void 차량_등록_실패_회사를_찾을수없음() {
        // given
        VehicleCreateRequest request = new VehicleCreateRequest(
                "현대",
                2023,
                "아반떼",
                "12가3456",
                "검정",
                5000,
                37.5665,
                126.9780
        );

        given(companyRepository.findById(1L)).willReturn(Optional.empty());
        given(vehicleDetailRepository.save(any(VehicleDetail.class))).willReturn(mock(VehicleDetail.class));

        // when & then
        CustomException exception = assertThrows(CustomException.class,
                () -> vehicleService.registerVehicle(request));

        verify(vehicleDetailRepository).save(vehicleDetailCaptor.capture());
        verify(companyRepository).findById(1L);
        verify(vehicleRepository, never()).save(any(Vehicle.class));

        VehicleDetail capturedDetail = vehicleDetailCaptor.getValue();

        assertEquals("현대", capturedDetail.getManufacturer());
        assertEquals("아반떼", capturedDetail.getModel());
        assertEquals(2023, capturedDetail.getModelYear());
        assertEquals(ErrorCode.COMPANY_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("차량 상세 조회 성공")
    void 차량_조회_성공() {
        // given
        Long vehicleId = 1L;
        Company company = Company.builder().name("테스트 회사").build();
        VehicleDetail vehicleDetail = VehicleDetail.builder()
                .manufacturer("현대")
                .modelYear(2023)
                .model("쏘나타")
                .build();
        Vehicle vehicle = Vehicle.builder()
                .company(company)
                .vehicleDetail(vehicleDetail)
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
        assertThat(response.companyName()).isEqualTo("테스트 회사");
        assertThat(response.carNumber()).isEqualTo("12가3456");
        assertThat(response.mileage()).isEqualTo(50000);
    }

    @Test
    @DisplayName("차량 상세 조회 실패 - 차량 없음 혹은 active = false")
    void 차량_조회_실패_차량_정보_없음() {
        // given
        Long vehicleId = 1L;
        when(vehicleRepository.findByIdAndActiveTrue(vehicleId)).thenReturn(Optional.empty());

        // when & then
        CustomException exception = assertThrows(CustomException.class, () -> {
            vehicleService.getVehicleDetail(vehicleId);
        });

        verify(vehicleRepository).findByIdAndActiveTrue(vehicleIdCaptor.capture());
        assertThat(vehicleIdCaptor.getValue()).isEqualTo(vehicleId);
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VEHICLE_NOT_FOUND);
    }

    @Test
    @DisplayName("차량 삭제 성공")
    void 차량_삭제_성공() {
        // given
        Vehicle mockVehicle = mock(Vehicle.class);
        given(vehicleRepository.findById(1L)).willReturn(Optional.of(mockVehicle));
        given(mockVehicle.isActive()).willReturn(true);

        // when
        vehicleService.deleteVehicle(1L);

        // then
        verify(vehicleRepository).findById(1L);
        verify(mockVehicle).delete();
    }

    @Test
    @DisplayName("차량 삭제 실패 - 차량이 존재하지 않는 경우")
    void 차량_삭제_실패_차량_미존재() {
        // given
        given(vehicleRepository.findById(1L)).willReturn(Optional.empty());

        // when & then
        CustomException exception = assertThrows(CustomException.class,
                () -> vehicleService.deleteVehicle(1L));

        verify(vehicleRepository).findById(1L);
        assertEquals(ErrorCode.VEHICLE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("차량 삭제 실패 - 이미 삭제된 차량인 경우")
    void 차량_삭제_실패_이미_삭제된_차량() {
        // given
        Vehicle mockVehicle = mock(Vehicle.class);
        given(vehicleRepository.findById(1L)).willReturn(Optional.of(mockVehicle));
        given(mockVehicle.isActive()).willReturn(false);

        // when & then
        CustomException exception = assertThrows(CustomException.class,
                () -> vehicleService.deleteVehicle(1L));

        verify(vehicleRepository).findById(1L);
        verify(mockVehicle, never()).delete();
        assertEquals(ErrorCode.VEHICLE_ALREADY_DELETED, exception.getErrorCode());
    }

    @Test
    @DisplayName("차량 목록 조회 성공 - 기본 페이지네이션")
    void 차량_목록_조회_성공_기본_페이지네이션() {
        // given
        PageRequest pageRequest = PageRequest.of(0, 10);
        List<Vehicle> vehicles = List.of(
                createMockVehicle("현대", "아반떼", "12가3456"),
                createMockVehicle("기아", "K5", "34나5678")
        );
        Page<Vehicle> mockPage = new PageImpl<>(vehicles);

        given(vehicleRepository.findAllByActiveTrue(pageRequest)).willReturn(mockPage);

        // when
        VehiclesResponse response = vehicleService.getVehicles(pageRequest);

        // then
        verify(vehicleRepository).findAllByActiveTrue(pageRequest);
        assertEquals(2, response.vehicles().size());
        assertEquals(1, response.totalPages());
        assertEquals(2L, response.totalElements());
        assertEquals(0, response.currentPage());
        assertEquals(2, response.size());
    }

    @Test
    @DisplayName("차량 목록 조회 성공 - 두 번째 페이지")
    void 차량_목록_조회_성공_두번째_페이지() {
        // given
        int pageSize = 10;
        PageRequest pageRequest = PageRequest.of(1, pageSize);

        List<Vehicle> secondPageVehicles = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            secondPageVehicles.add(createMockVehicle("쌍용", "티볼리", "56다" + (7890 + i)));
        }

        int totalElements = pageSize + 5;

        Page<Vehicle> mockPage = new PageImpl<>(secondPageVehicles, pageRequest, totalElements);

        given(vehicleRepository.findAllByActiveTrue(pageRequest)).willReturn(mockPage);

        // when
        VehiclesResponse response = vehicleService.getVehicles(pageRequest);

        // then
        verify(vehicleRepository).findAllByActiveTrue(pageRequest);
        assertEquals(5, response.vehicles().size());
        assertEquals(2, response.totalPages());
        assertEquals(totalElements, response.totalElements());
        assertEquals(1, response.currentPage());
        assertEquals(pageSize, response.size());
    }

    @Test
    @DisplayName("차량 목록 조회 성공 - 정렬 적용")
    void 차량_목록_조회_성공_정렬_적용() {
        // given
        PageRequest pageRequest = PageRequest.of(0, 10, Sort.by("carNumber").ascending());
        List<Vehicle> vehicles = List.of(
                createMockVehicle("기아", "K5", "34나5678"),
                createMockVehicle("현대", "아반떼", "12가3456")
        );
        Page<Vehicle> mockPage = new PageImpl<>(vehicles);

        given(vehicleRepository.findAllByActiveTrue(pageRequest)).willReturn(mockPage);

        // when
        VehiclesResponse response = vehicleService.getVehicles(pageRequest);

        // then
        verify(vehicleRepository).findAllByActiveTrue(pageRequest);
        assertEquals(2, response.vehicles().size());
        assertEquals("34나5678", response.vehicles().get(0).carNumber());
        assertEquals("12가3456", response.vehicles().get(1).carNumber());
    }

    @Test
    @DisplayName("차량 목록 조회 실패 - 페이지 크기 초과")
    void 차량_목록_조회_실패_페이지_크기_초과() {
        // given
        PageRequest pageRequest = PageRequest.of(0, 101);

        // when & then
        CustomException exception = assertThrows(CustomException.class,
                () -> vehicleService.getVehicles(pageRequest));

        assertEquals(ErrorCode.INVALID_PAGE_SIZE, exception.getErrorCode());
        verify(vehicleRepository, never()).findAllByActiveTrue(any());
    }

    @Test
    @DisplayName("차량 목록 조회 실패 - 유효하지 않은 정렬 기준")
    void 차량_목록_조회_실패_유효하지_않은_정렬_기준() {
        // given
        PageRequest pageRequest = PageRequest.of(0, 10, Sort.by("invalidProperty"));

        // when & then
        CustomException exception = assertThrows(CustomException.class,
                () -> vehicleService.getVehicles(pageRequest));

        assertEquals(ErrorCode.INVALID_SORT_PROPERTY, exception.getErrorCode());
        verify(vehicleRepository, never()).findAllByActiveTrue(any());
    }

    @Test
    @DisplayName("차량 정보 수정 성공")
    void 차량_정보_수정_성공() {
        // given
        Long vehicleId = 1L;
        VehicleUpdateRequest request = new VehicleUpdateRequest(
                "34가5678",
                "흰색",
                "기아",
                2024,
                "K5"
        );

        Vehicle mockVehicle = mock(Vehicle.class);
        VehicleDetail mockVehicleDetail = mock(VehicleDetail.class);

        when(vehicleRepository.findByIdAndActiveTrue(vehicleId)).thenReturn(Optional.of(mockVehicle));
        when(mockVehicle.getVehicleDetail()).thenReturn(mockVehicleDetail);
        when(mockVehicle.getCarNumber()).thenReturn("12가3456");

        // when
        vehicleService.updateVehicle(vehicleId, request);

        // then
        verify(vehicleRepository).findByIdAndActiveTrue(vehicleId);
        verify(mockVehicleDetail).update(request.manufacturer(), request.modelYear(), request.model());
        verify(mockVehicle).update(request.carNumber(), request.color());
    }

    @Test
    @DisplayName("차량 정보 수정 실패 - 차량 번호 중복")
    void 차량_정보_수정_실패_차량번호_중복() {
        // given
        Long vehicleId = 1L;
        String existingCarNumber = "12가3456";
        String newCarNumber = existingCarNumber; // 기존과 동일한 번호로 시도

        VehicleUpdateRequest request = new VehicleUpdateRequest(
                newCarNumber,
                "흰색",
                "기아",
                2024,
                "K5"
        );

        Vehicle mockVehicle = mock(Vehicle.class);
        when(vehicleRepository.findByIdAndActiveTrue(vehicleId)).thenReturn(Optional.of(mockVehicle));
        when(mockVehicle.getCarNumber()).thenReturn(existingCarNumber);
        when(vehicleRepository.existsByCarNumberAndActiveTrue(newCarNumber)).thenReturn(true);

        // when & then
        CustomException exception = assertThrows(CustomException.class,
                () -> vehicleService.updateVehicle(vehicleId, request));

        verify(vehicleRepository).findByIdAndActiveTrue(vehicleId);
        verify(vehicleRepository).existsByCarNumberAndActiveTrue(newCarNumber);
        assertEquals(ErrorCode.DUPLICATE_CAR_NUMBER, exception.getErrorCode());
    }

    @Test
    @DisplayName("차량 정보 수정 실패 - 차량을 찾을 수 없음")
    void 차량_정보_수정_실패_차량을_찾을_수_없음() {
        // given
        Long nonExistentVehicleId = 999L;
        VehicleUpdateRequest request = new VehicleUpdateRequest(
                "34가5678", "흰색", "기아", 2024, "K5"
        );

        when(vehicleRepository.findByIdAndActiveTrue(nonExistentVehicleId)).thenReturn(Optional.empty());

        // when & then
        CustomException exception = assertThrows(CustomException.class,
                () -> vehicleService.updateVehicle(nonExistentVehicleId, request));

        verify(vehicleRepository).findByIdAndActiveTrue(nonExistentVehicleId);
        assertEquals(ErrorCode.VEHICLE_NOT_FOUND, exception.getErrorCode());
    }

    private Vehicle createMockVehicle(String manufacturer, String model, String carNumber) {
        VehicleDetail vehicleDetail = mock(VehicleDetail.class);
        given(vehicleDetail.getManufacturer()).willReturn(manufacturer);
        given(vehicleDetail.getModel()).willReturn(model);
        given(vehicleDetail.getModelYear()).willReturn(2023);

        Company company = mock(Company.class);
        given(company.getId()).willReturn(1L);
        given(company.getName()).willReturn("샘플 회사");

        Vehicle vehicle = mock(Vehicle.class);
        given(vehicle.getId()).willReturn(1L);
        given(vehicle.getVehicleDetail()).willReturn(vehicleDetail);
        given(vehicle.getCompany()).willReturn(company);
        given(vehicle.getCarNumber()).willReturn(carNumber);
        given(vehicle.getColor()).willReturn("검정");
        given(vehicle.getMileage()).willReturn(5000);

        return vehicle;
    }
}
