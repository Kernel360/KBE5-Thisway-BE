package org.thisway.vehicle.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;
import org.thisway.vehicle.dto.request.VehicleModelCreateRequest;
import org.thisway.vehicle.service.VehicleModelService;

@SpringBootTest
@AutoConfigureMockMvc
class VehicleModelControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private VehicleModelService vehicleModelService;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("차량 모델 등록 성공 시 201 상태코드를 반환한다")
  @WithMockUser(roles = { "COMPANY_ADMIN" })
  void registerVehicleModel_Success() throws Exception {
    // given
    VehicleModelCreateRequest request = new VehicleModelCreateRequest(
        "현대", 2023, "아반떼");
    doNothing().when(vehicleModelService).registerVehicleModel(any(VehicleModelCreateRequest.class));

    // when & then
    mockMvc.perform(post("/api/vehicle-models")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andDo(print())
        .andExpect(status().isCreated());

    verify(vehicleModelService).registerVehicleModel(any(VehicleModelCreateRequest.class));
  }

  @Test
  @DisplayName("중복된 차량 모델 등록 시 예외가 발생한다")
  @WithMockUser(roles = { "COMPANY_ADMIN" })
  void registerVehicleModel_DuplicateModel() throws Exception {
    // given
    VehicleModelCreateRequest request = new VehicleModelCreateRequest(
        "현대", 2023, "아반떼");
    doThrow(new CustomException(ErrorCode.VEHICLE_MODEL_ALREADY_EXISTS))
        .when(vehicleModelService).registerVehicleModel(any(VehicleModelCreateRequest.class));

    // when & then
    mockMvc.perform(post("/api/vehicle-models")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andDo(print())
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("권한이 없는 사용자의 차량 모델 등록 시 예외가 발생한다")
  @WithMockUser(roles = { "MEMBER" })
  void registerVehicleModel_Unauthorized() throws Exception {
    // given
    VehicleModelCreateRequest request = new VehicleModelCreateRequest(
        "현대", 2023, "아반떼");
    doThrow(new CustomException(ErrorCode.AUTH_UNAUTHORIZED))
        .when(vehicleModelService).registerVehicleModel(any(VehicleModelCreateRequest.class));

    // when & then
    mockMvc.perform(post("/api/vehicle-models")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andDo(print())
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("유효하지 않은 요청으로 차량 모델 등록 시 예외가 발생한다")
  @WithMockUser(roles = { "COMPANY_ADMIN" })
  void registerVehicleModel_InvalidRequest() throws Exception {
    // given
    VehicleModelCreateRequest request = new VehicleModelCreateRequest(
        null, 2023, "아반떼");

    // when & then
    mockMvc.perform(post("/api/vehicle-models")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andDo(print())
        .andExpect(status().isBadRequest());
  }
}

