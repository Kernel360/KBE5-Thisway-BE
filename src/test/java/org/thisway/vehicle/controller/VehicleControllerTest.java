// java
package org.thisway.vehicle.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.thisway.common.ApiErrorResponse;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;
import org.thisway.vehicle.dto.request.VehicleCreateRequest;
import org.thisway.vehicle.dto.request.VehicleUpdateRequest;
import org.thisway.vehicle.dto.response.VehicleResponse;
import org.thisway.vehicle.dto.response.VehiclesResponse;
import org.thisway.vehicle.service.VehicleService;

@SpringBootTest
@AutoConfigureMockMvc
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
    @WithMockUser(roles = { "COMPANY_ADMIN" })
    void 차량_등록_요청_성공() throws Exception {
        VehicleCreateRequest request = new VehicleCreateRequest(
                "현대", 2022, "아반떼", "12가3456", "흰색");
        doNothing().when(vehicleService).registerVehicle(request);

        mockMvc.perform(
                post("/api/vehicles")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("차량 등록 요청 실패 - 업체 미존재")
    @WithMockUser(roles = { "COMPANY_ADMIN" })
    void 차량_등록_요청_실패() throws Exception {
        VehicleCreateRequest request = new VehicleCreateRequest(
                "현대", 2022, "아반떼", "12가3456", "흰색");
        doThrow(new CustomException(ErrorCode.COMPANY_NOT_FOUND))
                .when(vehicleService).registerVehicle(request);

        mockMvc.perform(
                post("/api/vehicles")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.COMPANY_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("차량 상세 조회 API 성공")
    @WithMockUser(roles = { "COMPANY_ADMIN" })
    void 차량_상세_조회_성공() throws Exception {
        // given
        Long vehicleId = 1L;
        VehicleResponse vehicleResponse = new VehicleResponse(
                vehicleId,
                "기아",
                2024,
                "K5",
                "34나5678",
                "검정",
                10000,
                true
        );
        given(vehicleService.getVehicleDetail(vehicleId)).willReturn(vehicleResponse);

        // when & then
        MvcResult mvcResult = mockMvc.perform(
                get("/api/vehicles/{id}", vehicleId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = mvcResult.getResponse().getContentAsString();
        VehicleResponse response = objectMapper.readValue(
                responseBody, VehicleResponse.class);

        assertThat(response.manufacturer()).isEqualTo("기아");
        assertThat(response.modelYear()).isEqualTo(2024);
        assertThat(response.model()).isEqualTo("K5");
        assertThat(response.carNumber()).isEqualTo("34나5678");
        assertThat(response.mileage()).isEqualTo(10000);
        assertThat(response.powerOn()).isEqualTo(true);
    }

    @Test
    @DisplayName("차량 상세 조회 API 실패 - 차량 없음")
    @WithMockUser(roles = { "MEMBER" })
    void 차량_상세_조회_실패() throws Exception {
        // given
        Long vehicleId = 1L;
        given(vehicleService.getVehicleDetail(vehicleId))
                .willThrow(new CustomException(ErrorCode.VEHICLE_NOT_FOUND));

        // when & then
        MvcResult mvcResult = mockMvc.perform(
                get("/api/vehicles/{id}", vehicleId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andReturn();

        String responseBody = mvcResult.getResponse().getContentAsString();
        ApiErrorResponse response = objectMapper.readValue(
                responseBody, ApiErrorResponse.class);
        assertThat(response.message()).isEqualTo(ErrorCode.VEHICLE_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("차량 삭제 요청 성공")
    @WithMockUser(roles = { "COMPANY_ADMIN" })
    void 차량_삭제_요청_성공() throws Exception {
        doNothing().when(vehicleService).deleteVehicle(1L);

        mockMvc.perform(
                delete("/api/vehicles/1"))
                .andDo(print())
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("차량 삭제 요청 실패 - 차량 미존재")
    @WithMockUser(roles = { "COMPANY_ADMIN" })
    void 차량_삭제_요청_실패() throws Exception {
        doThrow(new CustomException(ErrorCode.VEHICLE_NOT_FOUND))
                .when(vehicleService).deleteVehicle(1L);

        MvcResult mvcResult = mockMvc.perform(
                delete("/api/vehicles/1"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andReturn();

        String responseBody = mvcResult.getResponse().getContentAsString();
        ApiErrorResponse response = objectMapper.readValue(
                responseBody, ApiErrorResponse.class);
        assertThat(response.message()).isEqualTo(ErrorCode.VEHICLE_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("차량 삭제 요청 실패 - 이미 삭제된 차량")
    @WithMockUser(roles = { "COMPANY_ADMIN" })
    void 이미_삭제된_차량_삭제_요청_실패() throws Exception {
        // given
        doThrow(new CustomException(ErrorCode.VEHICLE_ALREADY_DELETED))
                .when(vehicleService).deleteVehicle(1L);

        // when & then
        MvcResult mvcResult = mockMvc.perform(
                delete("/api/vehicles/1"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andReturn();

        String responseBody = mvcResult.getResponse().getContentAsString();
        ApiErrorResponse response = objectMapper.readValue(
                responseBody, ApiErrorResponse.class);
        assertThat(response.message()).isEqualTo(ErrorCode.VEHICLE_ALREADY_DELETED.getMessage());
    }

    @Test
    @DisplayName("차량 목록 조회 성공 - 기본 페이지네이션")
    @WithMockUser(roles = { "COMPANY_ADMIN" })
    void 차량_목록_조회_성공_기본_페이지네이션() throws Exception {
        // given
        List<VehicleResponse> vehicles = List.of(
                new VehicleResponse(1L, "현대", 2023, "아반떼", "12가3456", "검정", 5000, false),
                new VehicleResponse(2L, "기아", 2023, "K5", "34나5678", "흰색", 3000, true));
        VehiclesResponse vehiclesResponse = new VehiclesResponse(vehicles, 1, 2, 0, 10);

        given(vehicleService.getVehicles(any()))
                .willReturn(vehiclesResponse);

        // when & then
        MvcResult mvcResult = mockMvc.perform(
                get("/api/vehicles"))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = mvcResult.getResponse().getContentAsString();
        VehiclesResponse response = objectMapper.readValue(
                responseBody, VehiclesResponse.class);

        assertThat(response.vehicles()).hasSize(2);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.totalElements()).isEqualTo(2);
        assertThat(response.currentPage()).isEqualTo(0);
        assertThat(response.size()).isEqualTo(10);
    }

    @Test
    @DisplayName("차량 목록 조회 성공 - 두 번째 페이지")
    @WithMockUser(roles = { "COMPANY_ADMIN" })
    void 차량_목록_조회_성공_두번째_페이지() throws Exception {
        // given
        List<VehicleResponse> vehicles = List.of(
                new VehicleResponse(3L, "쌍용", 2023, "티볼리", "56다7890", "파랑", 1000, false));
        Page<VehicleResponse> page = new PageImpl<>(vehicles);
        VehiclesResponse vehiclesResponse = new VehiclesResponse(vehicles, 2, 3, 1, 2);

        given(vehicleService.getVehicles(any())).willReturn(vehiclesResponse);

        // when & then
        MvcResult mvcResult = mockMvc.perform(
                get("/api/vehicles")
                        .param("page", "3")
                        .param("size", "2"))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = mvcResult.getResponse().getContentAsString();
        VehiclesResponse response = objectMapper.readValue(
                responseBody, VehiclesResponse.class);

        assertThat(response.vehicles()).hasSize(1);
        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(3);
        assertThat(response.currentPage()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("차량 목록 조회 성공 - 정렬 적용")
    @WithMockUser(roles = { "COMPANY_ADMIN" })
    void 차량_목록_조회_성공_정렬_적용() throws Exception {
        // given
        List<VehicleResponse> descendingOrder = List.of(
                new VehicleResponse(2L, "기아", 2023, "K5", "34나5678", "흰색", 3000, true),
                new VehicleResponse(1L, "현대", 2023, "아반떼", "12가3456", "검정", 5000, false));

        VehiclesResponse descResponse = new VehiclesResponse(descendingOrder, 1, 2, 0, 10);

        given(vehicleService.getVehicles(argThat(pageable -> pageable.getSort().getOrderFor("carNumber") != null &&
                pageable.getSort().getOrderFor("carNumber").getDirection().isDescending())))
                .willReturn(descResponse);

        // when & then
        MvcResult mvcResult = mockMvc.perform(
                get("/api/vehicles")
                        .param("sort", "carNumber,desc"))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = mvcResult.getResponse().getContentAsString();
        VehiclesResponse response = objectMapper.readValue(
                responseBody, VehiclesResponse.class);

        assertThat(response.vehicles()).hasSize(2);
        assertThat(response.vehicles().get(0).carNumber()).isEqualTo("34나5678");
        assertThat(response.vehicles().get(1).carNumber()).isEqualTo("12가3456");
    }

    @Test
    @DisplayName("차량 정보 수정 요청 성공")
    @WithMockUser(roles = { "COMPANY_ADMIN" })
    void 차량_정보_수정_요청_성공() throws Exception {
        // given
        Long vehicleId = 1L;
        VehicleUpdateRequest request = new VehicleUpdateRequest(
                "34가5678",
                "흰색",
                "기아",
                2024,
                "K5");
        doNothing().when(vehicleService).updateVehicle(vehicleId, request);

        // when & then
        mockMvc.perform(
                patch("/api/vehicles/{id}", vehicleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("차량 정보 수정 요청 실패 - 차량을 찾을 수 없음")
    @WithMockUser(roles = { "COMPANY_ADMIN" })
    void 차량_정보_수정_요청_실패_차량_미존재() throws Exception {
        // given
        Long vehicleId = 999L;
        VehicleUpdateRequest request = new VehicleUpdateRequest(
                "34가5678",
                "흰색",
                "기아",
                2024,
                "K5");
        doThrow(new CustomException(ErrorCode.VEHICLE_NOT_FOUND))
                .when(vehicleService).updateVehicle(vehicleId, request);

        // when & then
        MvcResult mvcResult = mockMvc.perform(
                patch("/api/vehicles/{id}", vehicleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andReturn();

        String responseBody = mvcResult.getResponse().getContentAsString();
        ApiErrorResponse response = objectMapper.readValue(
                responseBody, ApiErrorResponse.class);
        assertThat(response.message()).isEqualTo(ErrorCode.VEHICLE_NOT_FOUND.getMessage());
    }
}
