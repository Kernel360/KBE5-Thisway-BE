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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;
import org.thisway.vehicle.dto.request.VehicleCreateRequest;
import org.thisway.vehicle.dto.response.VehicleResponse;
import org.thisway.vehicle.service.VehicleService;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    @Test
    @DisplayName("차량 상세 조회 API 성공")
    void 차량_상세_조회_성공() throws Exception {
        // given
        Long vehicleId = 1L;
        VehicleResponse vehicleResponse = new VehicleResponse(
                "기아",
                2024,
                "K5",
                1L,
                "샘플 회사",
                "34나5678",
                10000
        );
        given(vehicleService.getVehicleDetail(vehicleId)).willReturn(vehicleResponse);

        // when & then
        mockMvc.perform(get("/api/vehicles/{id}", vehicleId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(HttpStatus.OK.value()))
                .andExpect(jsonPath("$.data.manufacturer").value("기아"))
                .andExpect(jsonPath("$.data.modelYear").value(2024))
                .andExpect(jsonPath("$.data.model").value("K5"))
                .andExpect(jsonPath("$.data.companyName").value("샘플 회사"))
                .andExpect(jsonPath("$.data.carNumber").value("34나5678"))
                .andExpect(jsonPath("$.data.mileage").value(10000));
    }

    @Test
    @DisplayName("차량 상세 조회 API 실패 - 차량 없음")
    void 차량_상세_조회_실패() throws Exception {
        // given
        Long vehicleId = 1L;
        given(vehicleService.getVehicleDetail(vehicleId))
                .willThrow(new CustomException(ErrorCode.VEHICLE_NOT_FOUND));

        // when & then
        mockMvc.perform(get("/api/vehicles/{id}", vehicleId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(ErrorCode.VEHICLE_NOT_FOUND.getStatusValue()))
                .andExpect(jsonPath("$.message").value(ErrorCode.VEHICLE_NOT_FOUND.getMessage()))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("차량 삭제 요청 성공")
    void 차량_삭제_요청_성공() throws Exception {
        doNothing().when(vehicleService).deleteVehicle(1L);

        mockMvc.perform(
                        delete("/api/vehicles/1")
                )
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("204"));
    }

    @Test
    @DisplayName("차량 삭제 요청 실패 - 차량 미존재")
    void 차량_삭제_요청_실패() throws Exception {
        doThrow(new CustomException(ErrorCode.VEHICLE_NOT_FOUND))
                .when(vehicleService).deleteVehicle(1L);

        mockMvc.perform(
                        delete("/api/vehicles/1")
                )
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("차량 정보를 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("차량 삭제 요청 실패 - 이미 삭제된 차량")
    void 이미_삭제된_차량_삭제_요청_실패() throws Exception {
        // given
        doThrow(new CustomException(ErrorCode.VEHICLE_ALREADY_DELETED))
                .when(vehicleService).deleteVehicle(1L);

        // when & then
        mockMvc.perform(
                        delete("/api/vehicles/1")
                )
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("400"))
                .andExpect(jsonPath("$.message").value("이미 삭제된 차량입니다."));
    }
}
