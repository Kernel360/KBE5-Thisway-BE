package org.thisway.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.thisway.common.ApiResponse;
import org.thisway.member.dto.request.MemberRegisterRequest;
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
}
