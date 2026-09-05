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
import org.thisway.member.application.AdminMemberService;

@SpringBootTest
@AutoConfigureMockMvc
@RequiredArgsConstructor
@TestConstructor(autowireMode = AutowireMode.ALL)
class AdminApiSecurityTest {

    private final MockMvc mockMvc;

    @MockitoBean
    private AdminMemberService adminMemberService;

    @Test
    @DisplayName("ADMIN은 실제 관리자 회원 삭제 API에 접근할 수 있다")
    @WithMockUser(roles = "ADMIN")
    void ADMIN은_관리자_회원삭제_API에_접근할수있다() throws Exception {
        mockMvc.perform(delete("/api/admin/members/{id}", 1L))
                .andExpect(status().isNoContent());

        verify(adminMemberService).deleteMember(1L);
    }

    @Test
    @DisplayName("여러 역할 중 ADMIN이 포함되면 관리자 회원 삭제 API에 접근할 수 있다")
    @WithMockUser(roles = {"ADMIN", "MEMBER"})
    void ADMIN이_포함된_역할은_관리자_회원삭제_API에_접근할수있다() throws Exception {
        mockMvc.perform(delete("/api/admin/members/{id}", 1L))
                .andExpect(status().isNoContent());

        verify(adminMemberService).deleteMember(1L);
    }

    @Test
    @DisplayName("ADMIN이 없는 역할은 관리자 회원 삭제 API에서 차단된다")
    @WithMockUser(roles = {"COMPANY_CHEF", "COMPANY_ADMIN", "MEMBER"})
    void ADMIN이_없는_역할은_관리자_회원삭제_API에서_차단된다() throws Exception {
        mockMvc.perform(delete("/api/admin/members/{id}", 1L))
                .andExpect(status().isForbidden());

        verifyNoInteractions(adminMemberService);
    }
}
