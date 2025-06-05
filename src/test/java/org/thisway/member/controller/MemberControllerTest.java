package org.thisway.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.thisway.common.ApiErrorResponse;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;
import org.thisway.common.PageInfo;
import org.thisway.member.dto.request.MemberRegisterRequest;
import org.thisway.member.dto.response.MemberResponse;
import org.thisway.member.dto.response.MembersResponse;
import org.thisway.member.service.MemberService;
import org.thisway.member.support.MemberFixture;


@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class MemberControllerTest {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    @MockitoBean
    private final MemberService memberService;

    @Test
    @DisplayName("멤버 조회가 정상적으로 되었을 때, ok 응답과 정상적으로 데이터를 조회할 수 있다.")
    @WithMockUser
    void 멤버_조회_테스트_성공() throws Exception {
        // when
        long id = 1L;
        MemberResponse expectResponse = MemberFixture.createMemberResponse(id);
        when(memberService.getMemberDetail(id))
                .thenReturn(expectResponse);

        MvcResult mvcResult = mockMvc.perform(
                        get("/api/members/1"))
                .andExpect(status().isOk())
                .andDo(print())
                .andReturn();

        // then
        String responseBody = mvcResult.getResponse().getContentAsString();
        MemberResponse response = objectMapper.readValue(
                responseBody, MemberResponse.class
        );

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(id);
    }

    @Test
    @DisplayName("멤버 조회시 없는 멤버 ID 요청이면, not found 응답을 한다.")
    @WithMockUser
    void 멤버_조회_테스트_없는_멤버_ID() throws Exception {
        when(memberService.getMemberDetail(1L))
                .thenThrow(new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        // when
        MvcResult mvcResult = mockMvc.perform(
                        get("/api/members/1")
                )
                .andExpect(status().isBadRequest())
                .andDo(print())
                .andReturn();

        // then
        String responseBody = mvcResult.getResponse().getContentAsString();
        ApiErrorResponse response = objectMapper.readValue(
                responseBody, ApiErrorResponse.class
        );
        assertThat(response.code()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("멤버 전체 조회가 정상적으로 되었을 때, ok 응답과 함께 정상적으로 데이터를 조회할 수 있다")
    @WithMockUser
    void 멤버_전체_조회_테스트_성공() throws Exception {
        // when
        MembersResponse expectResponse = MemberFixture.createMembersResponse(2);
        when(memberService.getMembers(any(Pageable.class)))
                .thenReturn(expectResponse);

        MvcResult mvcResult = mockMvc.perform(
                        get("/api/members")
                )
                .andExpect(status().isOk())
                .andDo(print())
                .andReturn();

        // then
        String responseBody = mvcResult.getResponse().getContentAsString();
        MembersResponse response = objectMapper.readValue(
                responseBody, MembersResponse.class
        );

        assertThat(response).isNotNull();

        PageInfo pageInfo = response.pageInfo();
        assertThat(pageInfo.totalElements()).isEqualTo(2);
        assertThat(pageInfo.numberOfElements()).isEqualTo(2);
        assertThat(pageInfo.totalPages()).isEqualTo(1);
        assertThat(pageInfo.currentPage()).isEqualTo(0);
        assertThat(pageInfo.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("멤버 등록이 정상적으로 되었을 때, created 응답을 한다.")
    @WithMockUser
    void 멤버_등록_테스트_성공() throws Exception {
        // given
        MemberRegisterRequest request = MemberFixture.createMemberRegisterRequestWithCompanyId(1L);

        // when
        MvcResult mvcResult = mockMvc.perform(
                        post("/api/members")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isCreated())
                .andDo(print())
                .andReturn();

        // then
        String responseBody = mvcResult.getResponse().getContentAsString();
    }

    @Test
    @DisplayName("멤버 삭제가 정상적으로 되었을 때, no content 응답을 한다.")
    @WithMockUser
    void 멤버_삭제_테스트_성공() throws Exception {
        // when
        MvcResult mvcResult = mockMvc.perform(
                        delete("/api/members/1"))
                .andExpect(status().isNoContent())
                .andDo(print())
                .andReturn();

        // then
    }

    @Test
    @DisplayName("멤버 삭제시 없는 멤버의 ID 요청이면, not found 응답을 한다.")
    @WithMockUser
    void 멤버_삭제_테스트_없는_멤버_ID() throws Exception {
        // when
        BDDMockito.willThrow(new CustomException(ErrorCode.MEMBER_NOT_FOUND))
                .given(memberService).deleteMember(eq(1L));

        MvcResult mvcResult = mockMvc.perform(
                        delete("/api/members/1"))
                .andExpect(status().isBadRequest())
                .andDo(print())
                .andReturn();

        // then
        String responseBody = mvcResult.getResponse().getContentAsString();
        ApiErrorResponse response = objectMapper.readValue(
                responseBody, ApiErrorResponse.class
        );
        assertThat(response.code()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND.getCode());
    }
}
