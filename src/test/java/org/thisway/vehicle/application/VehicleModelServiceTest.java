package org.thisway.vehicle.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.thisway.common.BaseEntity;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;
import org.thisway.member.domain.Member;
import org.thisway.member.domain.MemberRole;
import org.thisway.security.service.SecurityService;
import org.thisway.vehicle.vehicle_model.application.VehicleModelService;
import org.thisway.vehicle.vehicle_model.domain.VehicleModel;
import org.thisway.vehicle.vehicle_model.interfaces.VehicleModelCreateRequest;
import org.thisway.vehicle.vehicle_model.infrastructure.VehicleModelRepository;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VehicleModelServiceTest {
    @Mock
    private VehicleModelRepository vehicleModelRepository;
    @Mock
    private SecurityService securityService;
    @InjectMocks
    private VehicleModelService vehicleModelService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("중복 차량 모델 등록 시 예외 발생")
    void registerVehicleModel_duplicate() {
        VehicleModelCreateRequest request = new VehicleModelCreateRequest("현대", 2024, "아반떼");
        Member member = Member.builder()
                .role(MemberRole.ADMIN)
                .name("관리자")
                .email("admin@example.com")
                .password("password")
                .phone("01012345678")
                .memo("테스트")
                .build();
        when(securityService.getCurrentMember()).thenReturn(member);
        when(vehicleModelRepository.existsByManufacturerAndNameAndModelYear(any(), any(), any())).thenReturn(true);

        CustomException ex = assertThrows(CustomException.class, () -> vehicleModelService.registerVehicleModel(request));
        assertEquals(ErrorCode.VEHICLE_MODEL_ALREADY_EXISTS, ex.getErrorCode());
    }

    @Test
    @DisplayName("정상 등록 시 save 호출")
    void registerVehicleModel_success() {
        VehicleModelCreateRequest request = new VehicleModelCreateRequest("현대", 2024, "아반떼");
        Member member = Member.builder()
                .role(MemberRole.ADMIN)
                .name("관리자")
                .email("admin@example.com")
                .password("password")
                .phone("01012345678")
                .memo("테스트")
                .build();
        when(securityService.getCurrentMember()).thenReturn(member);
        when(vehicleModelRepository.existsByManufacturerAndNameAndModelYear(any(), any(), any())).thenReturn(false);
        VehicleModel savedVehicleModel = VehicleModel.builder()
                .manufacturer("현대")
                .modelYear(2024)
                .name("아반떼")
                .build();
        try {
            Field idField = BaseEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(savedVehicleModel, 1L);
        } catch (Exception ignore) {
        }

        when(vehicleModelRepository.save(any(VehicleModel.class))).thenReturn(savedVehicleModel);

        assertDoesNotThrow(() -> vehicleModelService.registerVehicleModel(request));
        verify(vehicleModelRepository, times(1)).save(any(VehicleModel.class));
    }
}
