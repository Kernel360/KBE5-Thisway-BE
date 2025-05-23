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
import org.thisway.vehicle.entity.Vehicle;
import org.thisway.vehicle.entity.VehicleDetail;
import org.thisway.vehicle.repository.VehicleDetailRepository;
import org.thisway.vehicle.repository.VehicleRepository;

import java.util.Optional;

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

    @Test
    @DisplayName("차량 등록이 성공하는 경우")
    void 차량_등록_성공() {
        // given
        VehicleCreateRequest request = new VehicleCreateRequest(
                "현대", 2023, "아반떼", "12가3456", "검정", 5000,
                true, 37.5665, 126.9780
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
        assertEquals(true, capturedVehicle.isOn());
    }

    @Test
    @DisplayName("회사를 찾을 수 없는 경우 차량 등록 불가")
    void 차량_등록_실패_회사를_찾을수없음() {
        // given
        VehicleCreateRequest request = new VehicleCreateRequest(
                "현대", 2023, "아반떼", "12가3456", "검정", 5000,
                true, 37.5665, 126.9780
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
}
