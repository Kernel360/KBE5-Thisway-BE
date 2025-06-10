package org.thisway.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@RequiredArgsConstructor
@TestConstructor(autowireMode = AutowireMode.ALL)
public class CompanyChefApiSecurityTest {

    private final MockMvc mockMvc;

    @Test
    @DisplayName("COMPANY_CHEF 권한으로 /api/company-chef/** 접근이 가능하다.")
    @WithMockUser(roles = "COMPANY_CHEF")
    void 관리자_API_접근_테스트() throws Exception {
        mockMvc.perform(get("/api/company-chef/test"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("COMPANY_CHEF 권한이 포함된 권한으로 /api/company-chef/** 접근이 가능하다.")
    @WithMockUser(roles = {"COMPANY_CHEF", "MEMBER"})
    void 관리자_API_접근_테스트_관리자_포함_권한() throws Exception {
        mockMvc.perform(get("/api/company-chef/test"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("관리자 이외의 권한으로 /api/company-chef/** 접근이 차단되어야 한다")
    @WithMockUser(roles = {"COMPANY_ADMIN", "MEMBER"})
    @Disabled
    void 관리자_API_접근_테스트_관리자_이외의_권한() throws Exception {
        mockMvc.perform(get("/api/company-chef/test"))
                .andExpect(status().isForbidden());
    }
}
