package org.thisway.member.interfaces;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.thisway.common.ApiErrorResponse;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;
import org.thisway.logging.config.LoggingConfig;
import org.thisway.member.application.PasswordService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PasswordController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@Import(LoggingConfig.class)
@RequiredArgsConstructor
public class PasswordControllerTest {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    @MockitoBean
    private final PasswordService passwordService;

    @Test
    @DisplayName("이메일 인증 코드 전송에 성공했을 때, ok 응답을 한다.")
    void 인증코드_전송_성공시_OK_응답() throws Exception {

        String email = "abc@exmaple.com";
        SendVerificationCodeRequest request = new SendVerificationCodeRequest(email);

        MvcResult mvcResult = mockMvc.perform(
                        post("/api/auth/verify-code")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isOk())
                .andDo(print())
                .andReturn();

        verify(passwordService).sendVerificationCode(email);
        Integer status = mvcResult.getResponse().getStatus();
        assertThat(status).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    @DisplayName("인증코드 요청 시 유효하지 않은 이메일을 입력하면, member_not_found 응답을 한다.")
    void 유효하지_않은_이메일로_인증코드_요청시_MEMBER_NOT_FOUND_응답() throws Exception {
        doThrow(new CustomException(ErrorCode.MEMBER_NOT_FOUND)).when(passwordService)
                .sendVerificationCode(anyString());

        SendVerificationCodeRequest request = new SendVerificationCodeRequest("abc@example.com");
        MvcResult mvcResult = mockMvc.perform(
                        post("/api/auth/verify-code")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest())
                .andDo(print())
                .andReturn();

        String responseBody = mvcResult.getResponse().getContentAsString();
        ApiErrorResponse response = objectMapper.readValue(
                responseBody, ApiErrorResponse.class
        );
        assertThat(response.code()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("비밀번호 변경 요청 시 올바른 인증 코드를 입력하면, ok 응답을 한다.")
    void 비밀번호_변경_성공시_OK_응답() throws Exception {

        PasswordChangeRequest request = new PasswordChangeRequest("abc@example.com", "123456", "theNewPassword");

        MvcResult mvcResult = mockMvc.perform(
                        put("/api/auth/password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isOk())
                .andDo(print())
                .andReturn();

        verify(passwordService).changePassword("abc@example.com", "123456", "theNewPassword");
        Integer status = mvcResult.getResponse().getStatus();
        assertThat(status).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    @DisplayName("비밀번호 변경 요청 시 유효하지 않은 인증 코드를 입력하면, Bad Request 응답을 한다.")
    void 유효하지_않은_인증코드로_비밀번호_변경_요청시_BAD_REQUEST_응답() throws Exception {

        doThrow(new CustomException(ErrorCode.AUTH_INVALID_VERIFICATION_CODE))
                .when(passwordService).changePassword(anyString(), anyString(), anyString());

        PasswordChangeRequest request = new PasswordChangeRequest("abc@example.com", "123456", "theNewPassword");
        MvcResult mvcResult = mockMvc.perform(
                        put("/api/auth/password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest())
                .andDo(print())
                .andReturn();

        String responseBody = mvcResult.getResponse().getContentAsString();
        ApiErrorResponse response = objectMapper.readValue(
                responseBody, ApiErrorResponse.class
        );
        assertThat(response.code()).isEqualTo(ErrorCode.AUTH_INVALID_VERIFICATION_CODE.getCode());
    }

}
