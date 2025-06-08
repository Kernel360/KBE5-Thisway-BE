package org.thisway.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.thisway.common.PageInfo;
import org.thisway.member.dto.MemberDto;
import org.thisway.member.dto.MembersDto;
import org.thisway.member.dto.request.AdminMemberRegisterRequest;
import org.thisway.member.dto.response.MembersResponse;
import org.thisway.member.entity.MemberRole;
import org.thisway.member.service.AdminMemberService;

@SpringBootTest
@AutoConfigureMockMvc
@RequiredArgsConstructor
@TestConstructor(autowireMode = AutowireMode.ALL)
class AdminMemberControllerTest {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    @MockitoBean
    private AdminMemberService adminMemberService;

    @Test
    @DisplayName("멤버 리스트를 조회할 수 있다.")
    @WithMockUser(authorities = "ADMIN")
    void 멤버_리스트_조회_테스트() throws Exception {
        // given
        MemberDto memberDto = new MemberDto(1L, 1L, MemberRole.MEMBER, "name", "email", "phone", "memo");
        PageInfo pageInfo = new PageInfo(1, 1, 1, 0, 10);
        MembersDto membersDto = new MembersDto(List.of(memberDto), pageInfo);

        given(adminMemberService.getMembers(any()))
                .willReturn(membersDto);

        // when
        String responseBody = mockMvc.perform(get("/api/admin/members"))
                .andExpect(status().isOk())
                .andDo(print())
                .andReturn()
                .getResponse()
                .getContentAsString();

        MembersResponse response = objectMapper.readValue(
                responseBody, MembersResponse.class
        );

        // then
        assertThat(response.pageInfo()).isEqualTo(pageInfo);
        assertThat(response.members()).hasSize(1);
        assertThat(response.members().getFirst().id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("멤버를 등록할 수 있다.")
    @WithMockUser(authorities = "ADMIN")
    void 멤버_등록_테스트() throws Exception {
        // given
        AdminMemberRegisterRequest request = new AdminMemberRegisterRequest(
                1L,
                "name",
                "email",
                "password",
                "phone",
                "memo"
        );
        String requestBody = objectMapper.writeValueAsString(request);

        // when & then
        mockMvc.perform(post("/api/admin/members")
                        .contentType(APPLICATION_JSON)
                        .content(requestBody)
                )
                .andExpect(status().isCreated())
                .andDo(print());
    }
}
