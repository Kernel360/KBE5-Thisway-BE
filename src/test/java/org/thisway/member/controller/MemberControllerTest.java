package org.thisway.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.thisway.common.ApiResponse;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;
import org.thisway.member.dto.request.MemberRegisterRequest;
import org.thisway.member.dto.response.MemberResponse;
import org.thisway.member.service.MemberService;
import org.thisway.member.support.MemberFixture;

@WebMvcTest(MemberController.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class MemberControllerTest {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    @MockitoBean
    private final MemberService memberService;

    @Test
    @DisplayName("멤버 조회가 정상적으로 되었을 때, ok 응답과 정상적으로 데이터를 조회할 수 있다.")
    void givenValidMemberId_whenGetMemberDetail_thenReturnsOkStatusWithMemberData() throws Exception {
        // when
        long id = 1L;
        MemberResponse expectResponse = MemberFixture.createMemberResponse(id);
        when(memberService.getMemberDetail(id))
                .thenReturn(expectResponse);

        MvcResult mvcResult = mockMvc.perform(
                        get("/api/members/1")
                )
                .andExpect(status().isOk())
                .andDo(print())
                .andReturn();

        // then
        String responseBody = mvcResult.getResponse().getContentAsString();
        ApiResponse<MemberResponse> response = objectMapper.readValue(
                responseBody, new TypeReference<ApiResponse<MemberResponse>>() {}
        );
        assertThat(response.status()).isEqualTo(HttpStatus.OK.value());

        MemberResponse memberResponse = response.data();
        assertThat(memberResponse).isNotNull();
        assertThat(memberResponse.id()).isEqualTo(id);
    }

    @Test
    @DisplayName("멤버 조회시 없는 멤버 ID 요청이면, not found 응답을 한다.")
    void givenNotFoundMemberId_whenGetMemberDetail_thenReturnsNotFoundStatus() throws Exception {
        when(memberService.getMemberDetail(1L))
                .thenThrow(new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        // when
        MvcResult mvcResult = mockMvc.perform(
                        get ("/api/members/1")
                )
                .andExpect(status().isOk())
                .andDo(print())
                .andReturn();

        // then
        String responseBody = mvcResult.getResponse().getContentAsString();
        ApiResponse<MemberResponse> response = objectMapper.readValue(
                responseBody, new TypeReference<ApiResponse<MemberResponse>>() {}
        );
        assertThat(response.status()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    @DisplayName("멤버 등록이 정상적으로 되었을 때, created 응답을 한다.")
    void givenValidRequest_whenRegisterMember_thenReturnsCreatedStatus() throws Exception {
        // given
        MemberRegisterRequest request = MemberFixture.createMemberRegisterRequest();

        // when
        MvcResult mvcResult = mockMvc.perform(
                post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isOk())
                .andDo(print())
                .andReturn();

        // then
        String responseBody = mvcResult.getResponse().getContentAsString();
        ApiResponse<Void> response = objectMapper.readValue(responseBody, new TypeReference<ApiResponse<Void>>() {});
        assertThat(response.status()).isEqualTo(HttpStatus.CREATED.value());
    }

    @Test
    @DisplayName("멤버 삭제가 정상적으로 되었을 때, no content 응답을 한다.")
    void givenValidMemberId_whenDeleteMember_thenReturnsNoContentStatus() throws Exception {
        // when
        MvcResult mvcResult = mockMvc.perform(
                        delete("/api/members/1")
                )
                .andExpect(status().isOk())
                .andDo(print())
                .andReturn();

        // then
        String responseBody = mvcResult.getResponse().getContentAsString();
        ApiResponse<Void> response = objectMapper.readValue(responseBody, new TypeReference<ApiResponse<Void>>() {});
        assertThat(response.status()).isEqualTo(HttpStatus.NO_CONTENT.value());
    }

    @Test
    @DisplayName("멤버 삭제시 없는 멤버의 ID 요청이면, not found 응답을 한다.")
    void givenNotFoundMemberId_whenDeleteMember_thenReturnsNotFoundStatus() throws Exception {
        // when
        BDDMockito.willThrow(new CustomException(ErrorCode.MEMBER_NOT_FOUND))
                .given(memberService).deleteMember(eq(1L));

        MvcResult mvcResult = mockMvc.perform(
                        delete("/api/members/1")
                )
                .andExpect(status().isOk())
                .andDo(print())
                .andReturn();

        // then
        String responseBody = mvcResult.getResponse().getContentAsString();
        ApiResponse<Void> response = objectMapper.readValue(responseBody, new TypeReference<ApiResponse<Void>>() {});
        assertThat(response.status()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }
}
