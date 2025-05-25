// java
package org.thisway.vehicle.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;
import org.thisway.vehicle.dto.request.VehicleCreateRequest;
import org.thisway.vehicle.service.VehicleService;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(VehicleController.class)
class VehicleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private VehicleService vehicleService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TestConfiguration
    static class MockConfig {
        @Bean
        public VehicleService vehicleService() {
            return Mockito.mock(VehicleService.class);
        }
    }

    @Test
    @DisplayName("차량 등록 요청 성공")
    void 차량_등록_요청_성공() throws Exception {
        VehicleCreateRequest request = new VehicleCreateRequest(
                "현대", 2022, "아반떼", "12가3456", "흰색",
                1000, 37.5665, 126.9780);
        doNothing().when(vehicleService).registerVehicle(request);

        mockMvc.perform(
                        post("/api/vehicles")
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(request))
                )
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("201"));
    }

    @Test
    @DisplayName("차량 등록 요청 실패 - 업체 미존재")
    void 차량_등록_요청_실패() throws Exception {
        VehicleCreateRequest request = new VehicleCreateRequest(
                "현대", 2022, "아반떼", "12가3456", "흰색",
                1000,37.5665, 126.9780);
        doThrow(new CustomException(ErrorCode.COMPANY_NOT_FOUND))
                .when(vehicleService).registerVehicle(request);

        mockMvc.perform(
                        post("/api/vehicles")
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(request))
                )
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("회사 정보를 찾을 수 없습니다."));
    }
}
