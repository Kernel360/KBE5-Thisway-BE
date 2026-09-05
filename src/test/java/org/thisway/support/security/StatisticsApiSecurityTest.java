package org.thisway.support.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.thisway.company.statistics.application.StatisticService;

import java.time.LocalDate;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StatisticsApiSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StatisticService statisticService;

    @Test
    @DisplayName("ADMIN은 특정 회사의 통계 저장을 수동 실행할 수 있다")
    @WithMockUser(roles = "ADMIN")
    void ADMIN은_통계저장_API를_호출할수있다() throws Exception {
        mockMvc.perform(post("/api/statistics/save")
                        .param("companyId", "1")
                        .param("targetDate", "2026-09-04"))
                .andExpect(status().isOk());

        verify(statisticService).saveStatistics(1L, LocalDate.of(2026, 9, 4));
    }

    @Test
    @DisplayName("회사 역할은 다른 회사 ID를 지정할 수 있는 통계 저장 API에서 차단된다")
    @WithMockUser(roles = {"COMPANY_CHEF", "COMPANY_ADMIN", "MEMBER"})
    void 회사_역할은_통계저장_API에서_차단된다() throws Exception {
        mockMvc.perform(post("/api/statistics/save")
                        .param("companyId", "999")
                        .param("targetDate", "2026-09-04"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(statisticService);
    }

    @Test
    @DisplayName("인증되지 않은 사용자는 통계 저장 API에서 차단된다")
    void 인증되지_않은_사용자는_통계저장_API에서_차단된다() throws Exception {
        mockMvc.perform(post("/api/statistics/save")
                        .param("companyId", "1")
                        .param("targetDate", "2026-09-04"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(statisticService);
    }
}
