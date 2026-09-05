package org.thisway.support.security;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.thisway.member.application.CompanyChefMemberService;

@SpringBootTest
@AutoConfigureMockMvc
@RequiredArgsConstructor
@TestConstructor(autowireMode = AutowireMode.ALL)
public class CompanyChefApiSecurityTest {

    private final MockMvc mockMvc;

    @MockitoBean
    private CompanyChefMemberService companyChefMemberService;

    @Test
    @DisplayName("COMPANY_CHEF는 실제 회사 회원 삭제 API에 접근할 수 있다")
    @WithMockUser(roles = "COMPANY_CHEF")
    void COMPANY_CHEF는_회사_회원삭제_API에_접근할수있다() throws Exception {
        mockMvc.perform(delete("/api/company-chef/members/{id}", 1L))
                .andExpect(status().isNoContent());

        verify(companyChefMemberService).deleteMember(1L);
    }

    @Test
    @DisplayName("여러 역할 중 COMPANY_CHEF가 포함되면 회사 회원 삭제 API에 접근할 수 있다")
    @WithMockUser(roles = {"COMPANY_CHEF", "MEMBER"})
    void COMPANY_CHEF가_포함된_역할은_회사_회원삭제_API에_접근할수있다() throws Exception {
        mockMvc.perform(delete("/api/company-chef/members/{id}", 1L))
                .andExpect(status().isNoContent());

        verify(companyChefMemberService).deleteMember(1L);
    }

    @Test
    @DisplayName("COMPANY_CHEF가 없는 역할은 회사 회원 삭제 API에서 차단된다")
    @WithMockUser(roles = {"COMPANY_ADMIN", "MEMBER"})
    void COMPANY_CHEF가_없는_역할은_회사_회원삭제_API에서_차단된다() throws Exception {
        mockMvc.perform(delete("/api/company-chef/members/{id}", 1L))
                .andExpect(status().isForbidden());

        verifyNoInteractions(companyChefMemberService);
    }
}
