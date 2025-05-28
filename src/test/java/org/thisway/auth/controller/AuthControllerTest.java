package org.thisway.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.thisway.auth.dto.VerificationPayload;
import org.thisway.auth.dto.request.PasswordChangeRequest;
import org.thisway.auth.dto.request.SendVerifyCodeRequest;
import org.thisway.auth.service.EmailVerificationService;
import org.thisway.common.ApiResponse;
import org.thisway.company.entity.Company;
import org.thisway.company.repository.CompanyRepository;
import org.thisway.company.support.CompanyFixture;
import org.thisway.member.entity.Member;
import org.thisway.member.repository.MemberRepository;
import org.thisway.member.support.MemberFixture;


@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
public class AuthControllerTest {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    @MockitoSpyBean
    private final EmailVerificationService emailVerificationService;
    private final MemberRepository memberRepository;
    private final CompanyRepository companyRepository;

    @BeforeEach
    void setUp() {
        memberRepository.deleteAll();
        companyRepository.deleteAll();
    }

    @Test
    @DisplayName("이메일 인증 코드 전송에 성공했을 때, ok 응답을 한다.")
    void givenValidEmail_whenSendVerifyCode_thenReturnOkStatus() throws Exception {
        Company company = companyRepository.save(CompanyFixture.createCompany());
        Member member = memberRepository.save(MemberFixture.createMember(company));
        SendVerifyCodeRequest request = new SendVerifyCodeRequest(member.getEmail());

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
                .andExpect(status().isOk())
                .andDo(print())
                .andReturn();

        String responseBody = mvcResult.getResponse().getContentAsString();
        ApiResponse<?> response = objectMapper.readValue(responseBody, ApiResponse.class);
        assertThat(response.status()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    @DisplayName("이메일 인증 코드 요청 시 비활성화 상태의 이메일을 입력하면, not_found 응답을 한다.")
    void givenInactiveEmail_whenSendVerifyCode_thenReturnNotFoundStatus() throws Exception {
        Company company = companyRepository.save(CompanyFixture.createCompany());
        Member member = memberRepository.save(MemberFixture.createMember(company));
        member.delete();
        memberRepository.save(member);

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
        assertThat(response.status()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    @DisplayName("비밀번호 변경 요청 시 올바른 인증 코드를 입력하면, ok 응답을 한다.")
    void givenValidCode_whenChangePassword_thenReturnOkStatus() throws Exception {
        Company company = companyRepository.save(CompanyFixture.createCompany());
        Member member = memberRepository.save(MemberFixture.createMember(company));

        VerificationPayload entry = new VerificationPayload("123456", System.currentTimeMillis() + 60000);
        doReturn(entry).when(emailVerificationService).retrieveFromRedis(any(String.class));

        PasswordChangeRequest request = new PasswordChangeRequest(member.getEmail(), "123456", "theNewPassword");
        MvcResult mvcResult = mockMvc.perform(
                put("/api/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isOk())
                .andDo(print())
                .andReturn();

        String responseBody = mvcResult.getResponse().getContentAsString();
        ApiResponse<?> response = objectMapper.readValue(responseBody, ApiResponse.class);
        assertThat(response.status()).isEqualTo(HttpStatus.OK.value());
        Member updatedMember = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(updatedMember.getPassword()).isEqualTo("theNewPassword");
    }

    @Test
    @DisplayName("비밀번호 변경 요청 시 잘못된 인증 코드를 입력하면, Bad Request 응답을 한다.")
    void givenInValidCode_whenChangePassword_thenReturnBadRequestStatus() throws Exception {
        Company company = companyRepository.save(CompanyFixture.createCompany());
        Member member = memberRepository.save(MemberFixture.createMember(company));

        VerificationPayload entry = new VerificationPayload("123456", System.currentTimeMillis() + 60000);
        doReturn(entry).when(emailVerificationService).retrieveFromRedis(any(String.class));

        PasswordChangeRequest request = new PasswordChangeRequest(member.getEmail(), "654321", "theNewPassword");
        MvcResult mvcResult = mockMvc.perform(
                put("/api/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isOk())
                .andDo(print())
                .andReturn();

        String responseBody = mvcResult.getResponse().getContentAsString();
        ApiResponse<?> response = objectMapper.readValue(responseBody, ApiResponse.class);
        assertThat(response.status()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    @DisplayName("비밀번호 변경 요청 시 만료된 인증 코드를 입력하면, Bad Request 응답을 한다.")
    void givenExpiredCode_whenChangePassword_thenReturnBadRequestStatus() throws Exception {
        Company company = companyRepository.save(CompanyFixture.createCompany());
        Member member = memberRepository.save(MemberFixture.createMember(company));

        VerificationPayload entry = new VerificationPayload("123456", System.currentTimeMillis() - 60000);
        doReturn(entry).when(emailVerificationService).retrieveFromRedis(any(String.class));

        PasswordChangeRequest request = new PasswordChangeRequest(member.getEmail(), "123456", "theNewPassword");
        MvcResult mvcResult = mockMvc.perform(
                put("/api/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isOk())
                .andDo(print())
                .andReturn();

        String responseBody = mvcResult.getResponse().getContentAsString();
        ApiResponse<?> response = objectMapper.readValue(responseBody, ApiResponse.class);
        assertThat(response.status()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

}
