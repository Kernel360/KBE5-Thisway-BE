package org.thisway.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.thisway.common.PageInfo;
import org.thisway.member.controller.dto.request.CompanyChefMemberRegisterRequest;
import org.thisway.member.controller.dto.request.CompanyChefMemberUpdateRequest;
import org.thisway.member.controller.dto.response.CompanyChefMemberDetailResponse;
import org.thisway.member.controller.dto.response.CompanyChefMemberSummaryResponse;
import org.thisway.member.controller.dto.response.CompanyChefMembersResponse;
import org.thisway.member.entity.MemberRole;
import org.thisway.member.service.CompanyChefMemberService;
import org.thisway.member.service.dto.CompanyChefMemberSearchCriteria;
import org.thisway.member.service.dto.output.CompanyChefMemberDetailOutput;
import org.thisway.member.service.dto.output.CompanyChefMemberSummaryOutput;
import org.thisway.member.service.dto.output.CompanyChefMembersOutput;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@SpringBootTest
@AutoConfigureMockMvc
@RequiredArgsConstructor
@TestConstructor(autowireMode = AutowireMode.ALL)
class CompanyChefMemberControllerTest {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    @MockitoBean
    private CompanyChefMemberService companyChefMemberService;

    @Test
    @DisplayName("멤버 상세 정보를 조회할 수 있다.")
    @WithMockUser(roles = "COMPANY_CHEF")
    void 멤버_상세_정보_조회_테스트() throws Exception {
        // given
        Long memberId = 1L;
        CompanyChefMemberDetailOutput memberDetailOutput = new CompanyChefMemberDetailOutput(
                memberId,
                MemberRole.MEMBER,
                "name",
                "email",
                "phone",
                "memo"
        );

        given(companyChefMemberService.getMemberDetail(memberId))
                .willReturn(memberDetailOutput);

        // when
        String responseBody = mockMvc.perform(get("/api/company-chef/members/" + memberId))
                .andExpect(status().isOk())
                .andDo(print())
                .andReturn()
                .getResponse()
                .getContentAsString();

        CompanyChefMemberDetailResponse response = objectMapper.readValue(
                responseBody, CompanyChefMemberDetailResponse.class
        );

        // then
        assertThat(response.id()).isEqualTo(memberId);
        assertThat(response.role()).isEqualTo(memberDetailOutput.role());
        assertThat(response.name()).isEqualTo(memberDetailOutput.name());
        assertThat(response.email()).isEqualTo(memberDetailOutput.email());
        assertThat(response.phone()).isEqualTo(memberDetailOutput.phone());
        assertThat(response.memo()).isEqualTo(memberDetailOutput.memo());
    }

    @Test
    @DisplayName("멤버 리스트를 조회할 수 있다.")
    @WithMockUser(roles = "COMPANY_CHEF")
    void 멤버_리스트_조회_테스트() throws Exception {
        // given
        CompanyChefMemberDetailOutput adminMemberDetailOutput = new CompanyChefMemberDetailOutput(
                1L,
                MemberRole.MEMBER,
                "name",
                "email",
                "phone",
                "memo"
        );
        PageInfo pageInfo = new PageInfo(1, 1, 1, 0, 10);
        CompanyChefMembersOutput adminMembersOutput = new CompanyChefMembersOutput(
                List.of(adminMemberDetailOutput),
                pageInfo
        );

        given(companyChefMemberService.getMembers(
            any(),
            any(CompanyChefMemberSearchCriteria.class)
        ))
                .willReturn(adminMembersOutput);

        // when
        String responseBody = mockMvc.perform(get("/api/company-chef/members"))
                .andExpect(status().isOk())
                .andDo(print())
                .andReturn()
                .getResponse()
                .getContentAsString();

        CompanyChefMembersResponse response = objectMapper.readValue(
                responseBody, CompanyChefMembersResponse.class
        );

        // then
        assertThat(response.pageInfo()).isEqualTo(pageInfo);
        assertThat(response.members()).hasSize(1);
        assertThat(response.members().getFirst().id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("멤버를 등록할 수 있다.")
    @WithMockUser(roles = "COMPANY_CHEF")
    void 멤버_등록_테스트() throws Exception {
        // given
        CompanyChefMemberRegisterRequest request = new CompanyChefMemberRegisterRequest(
                MemberRole.COMPANY_CHEF,
                "name",
                "email",
                "password",
                "phone",
                "memo"
        );
        String requestBody = objectMapper.writeValueAsString(request);

        // when & then
        mockMvc.perform(post("/api/company-chef/members")
                        .contentType(APPLICATION_JSON)
                        .content(requestBody)
                )
                .andExpect(status().isCreated())
                .andDo(print());
    }

    @Test
    @DisplayName("멤버를 수정할 수 있다.")
    @WithMockUser(roles = "COMPANY_CHEF")
    void 멤버_수정_테스트() throws Exception {
        // given
        CompanyChefMemberUpdateRequest request = new CompanyChefMemberUpdateRequest(
                "name",
                "email",
                "phone",
                "memo"
        );
        String requestBody = objectMapper.writeValueAsString(request);

        // when & then
        mockMvc.perform(put("/api/company-chef/members/" + 1L)
                        .contentType(APPLICATION_JSON)
                        .content(requestBody)
                )
                .andExpect(status().isOk())
                .andDo(print());
    }

    @Test
    @DisplayName("멤버를 삭제할 수 있다.")
    @WithMockUser(roles = "COMPANY_CHEF")
    void 멤버_삭제_테스트() throws Exception {
        // when & then
        mockMvc.perform(delete("/api/company-chef/members/" + 1L))
                .andExpect(status().isNoContent())
                .andDo(print());
    }

    @Test
    @DisplayName("멤버 요약을 조회할 수 있다.")
    @WithMockUser(roles = "COMPANY_CHEF")
    void 멤버_요약_조회_테스트() throws Exception {
        // given
        CompanyChefMemberSummaryOutput output = CompanyChefMemberSummaryOutput.builder()
                .companyChefCount(1)
                .companyAdminCount(2)
                .memberCount(3)
                .build();

        given(companyChefMemberService.summary())
                .willReturn(output);

        // when & then
        String responseBody = mockMvc.perform(get("/api/company-chef/members/summary"))
                .andExpect(status().isOk())
                .andDo(print())
                .andReturn()
                .getResponse()
                .getContentAsString();

        CompanyChefMemberSummaryResponse response = objectMapper.readValue(
                responseBody, CompanyChefMemberSummaryResponse.class
        );

        assertThat(response.companyChefCount()).isEqualTo(1);
        assertThat(response.companyAdminCount()).isEqualTo(2);
        assertThat(response.memberCount()).isEqualTo(3);
    }
}
