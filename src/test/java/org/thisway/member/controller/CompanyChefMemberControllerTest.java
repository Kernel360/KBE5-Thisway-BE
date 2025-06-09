package org.thisway.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.thisway.member.dto.CompanyChefMemberDetailOutput;
import org.thisway.member.dto.response.CompanyChefMemberDetailResponse;
import org.thisway.member.entity.MemberRole;
import org.thisway.member.service.CompanyChefMemberService;

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
    @WithMockUser(authorities = "COMPANY_CHEF")
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
}
