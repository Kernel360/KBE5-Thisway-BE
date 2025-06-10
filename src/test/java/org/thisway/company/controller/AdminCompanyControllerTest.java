package org.thisway.company.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.thisway.common.PageInfo;
import org.thisway.company.dto.AdminCompaniesOutput;
import org.thisway.company.dto.AdminCompanyDetailOutput;
import org.thisway.company.dto.request.AdminCompanyRegisterRequest;
import org.thisway.company.dto.response.AdminCompaniesResponse;
import org.thisway.company.dto.response.AdminCompanyDetailResponse;
import org.thisway.company.service.AdminCompanyService;

@SpringBootTest
@AutoConfigureMockMvc
@RequiredArgsConstructor
@TestConstructor(autowireMode = AutowireMode.ALL)
class AdminCompanyControllerTest {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    @MockitoBean
    private AdminCompanyService adminCompanyService;

    @Test
    @DisplayName("업체 상세정보를 조회할 수 있다.")
    @WithMockUser(roles = {"ADMIN"})
    void 업체_상세정보_조회_테스트() throws Exception {
        // given
        Long companyId = 1L;
        AdminCompanyDetailOutput expectedResponse = new AdminCompanyDetailOutput(
                companyId,
                "name",
                "crn",
                "contact",
                "addrRoad",
                "addrDetail",
                "memo",
                60
        );

        // when
        when(adminCompanyService.getCompanyDetail(companyId))
                .thenReturn(expectedResponse);

        String responseBody = mockMvc.perform(
                        get("/api/admin/companies/" + companyId)
                )
                .andExpect(status().isOk())
                .andDo(print())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // then
        AdminCompanyDetailResponse actualResponse = objectMapper.readValue(
                responseBody, AdminCompanyDetailResponse.class
        );
        assertThat(actualResponse)
                .usingRecursiveComparison()
                .isEqualTo(expectedResponse);
    }

    @Test
    @DisplayName("업체 리스트를 조회할 수 있다.")
    @WithMockUser(roles = {"ADMIN"})
    void 업체_리스트_조회_테스트() throws Exception {
        // given
        Long companyId = 1L;

        AdminCompanyDetailOutput adminCompanyDetailResponse = new AdminCompanyDetailOutput(
                companyId,
                "name",
                "crn",
                "contact",
                "addrRoad",
                "addrDetail",
                "memo",
                60
        );

        PageInfo pageInfo = new PageInfo(0, 10, 1, 1, 1);
        AdminCompaniesOutput expectedResponse = new AdminCompaniesOutput(List.of(adminCompanyDetailResponse), pageInfo);

        // when
        when(adminCompanyService.getCompanies(any()))
                .thenReturn(expectedResponse);

        String responseBody = mockMvc.perform(
                        get("/api/admin/companies")
                )
                .andExpect(status().isOk())
                .andDo(print())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // then
        AdminCompaniesResponse actualResponse = objectMapper.readValue(
                responseBody, AdminCompaniesResponse.class
        );

        assertThat(actualResponse)
                .usingRecursiveComparison()
                .isEqualTo(expectedResponse);
    }

    @Test
    @DisplayName("업체를 등록할 수 있다.")
    @WithMockUser(roles = {"ADMIN"})
    void 업체_등록_테스트() throws Exception {
        // given
        AdminCompanyRegisterRequest request = new AdminCompanyRegisterRequest(
                "name",
                "crn",
                "contact",
                "addrRoad",
                "addrDetail",
                "memo",
                60
        );

        // when & then
        mockMvc.perform(
                        MockMvcRequestBuilders.post("/api/admin/companies")
                                .contentType(APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isCreated())
                .andDo(print());
    }

    @Test
    @DisplayName("업체를 삭제할 수 있다.")
    @WithMockUser(roles = {"ADMIN"})
    void 업체_삭제_테스트() throws Exception {
        // given
        Long companyId = 1L;

        // when & then
        mockMvc.perform(
                        delete("/api/admin/companies/" + companyId)
                )
                .andExpect(status().isNoContent())
                .andDo(print());
    }
}
