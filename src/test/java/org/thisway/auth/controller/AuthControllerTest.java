package org.thisway.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.thisway.auth.dto.request.SendVerifyCodeRequest;
import org.thisway.common.ApiErrorResponse;
import org.thisway.common.ApiResponse;
import org.thisway.common.ErrorCode;
import org.thisway.member.entity.Member;
import org.thisway.member.repository.MemberRepository;
import org.thisway.member.support.MemberFixture;

@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
public class AuthControllerTest {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    private final MemberRepository memberRepository;

    @BeforeEach
    void setUp() {
        memberRepository.deleteAll();
        memberRepository.save(MemberFixture.createMember());
    }

    @Test
    @DisplayName("이메일 인증 코드 전송에 성공했을 때, ok 응답을 한다.")
    void givenValidEmail_whenSendVerifyCode_thenReturnOkStatus() throws Exception {
        SendVerifyCodeRequest request = new SendVerifyCodeRequest("hong@example.com");

        MvcResult mvcResult = mockMvc.perform(
                post("/api/auth/verify-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isOk())
                .andDo(print())
                .andReturn();

        String responseBody = mvcResult.getResponse().getContentAsString();
        ApiResponse<?> response = objectMapper.readValue(responseBody, ApiResponse.class);
        assertThat(response.status()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    @DisplayName("이메일 인증 코드 요청 시 존재하지 않는 이메일을 입력하면, not_found 응답을 한다.")
    void givenNonexistentEmail_whenSendVerifyCode_thenReturnNotFoundStatus() throws Exception {
        SendVerifyCodeRequest request = new SendVerifyCodeRequest("1234@example.com");

        MvcResult mvcResult = mockMvc.perform(
                post("/api/auth/verify-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest())
                .andDo(print())
                .andReturn();

        String responseBody = mvcResult.getResponse().getContentAsString();
        ApiErrorResponse response = objectMapper.readValue(responseBody, ApiErrorResponse.class);
        assertThat(response.code()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("이메일 인증 코드 요청 시 비활성화 상태의 이메일을 입력하면, not_found 응답을 한다.")
    void givenInactiveEmail_whenSendVerifyCode_thenReturnNotFoundStatus() throws Exception {
        Member member = memberRepository.findByEmailAndActiveTrue("hong@example.com").orElse(null);
        member.delete();
        memberRepository.save(member);

        SendVerifyCodeRequest request = new SendVerifyCodeRequest("hong@example.com");

        MvcResult mvcResult = mockMvc.perform(
                post("/api/auth/verify-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest())
                .andDo(print())
                .andReturn();

        String responseBody = mvcResult.getResponse().getContentAsString();
        ApiErrorResponse response = objectMapper.readValue(responseBody, ApiErrorResponse.class);
        assertThat(response.code()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND.getCode());
    }

}
