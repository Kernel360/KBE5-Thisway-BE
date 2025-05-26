package org.thisway.vehicle.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;
import org.thisway.company.entity.Company;
import org.thisway.company.repository.CompanyRepository;
import org.thisway.vehicle.dto.request.VehicleCreateRequest;
import org.thisway.vehicle.dto.response.VehicleResponse;
import org.thisway.vehicle.entity.Vehicle;
import org.thisway.vehicle.entity.VehicleDetail;
import org.thisway.vehicle.repository.VehicleDetailRepository;
import org.thisway.vehicle.repository.VehicleRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
}
