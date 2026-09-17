package org.thisway.support.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.thisway.member.application.AdminMemberService;
import org.thisway.member.domain.MemberReader;
import org.thisway.member.domain.MemberRole;
import org.thisway.support.security.utils.JwtTokenProvider;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fault injection at the reader port; the MySQL identity contract is tested separately. */
@SpringBootTest
@AutoConfigureMockMvc
class MemberAccountAdmissionAvailabilityTest {
    private static final long MEMBER_ID = 7L;
    private static final long COMPANY_ID = 11L;
    private static final String EMAIL = "admission-fault@example.test";

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokens;
    @MockitoBean MemberReader memberReader;
    @MockitoBean AdminMemberService adminMembers;

    @Test
    void 현재_계정이_일치하지_않으면_401이고_업무_서비스는_호출하지_않는다() throws Exception {
        mvc.perform(delete("/api/admin/members/99").header(AUTHORIZATION, bearer()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verify(memberReader).isCurrentIdentity(MEMBER_ID, EMAIL, COMPANY_ID, MemberRole.ADMIN);
        verifyNoInteractions(adminMembers);
    }

    @Test
    void DB_오류는_고정_500으로_거부하고_조회가_복구되면_다음_요청은_허용한다() throws Exception {
        when(memberReader.isCurrentIdentity(MEMBER_ID, EMAIL, COMPANY_ID, MemberRole.ADMIN))
                .thenThrow(new DataAccessResourceFailureException("fixture-private-database-detail"))
                .thenReturn(true);
        String authorization = bearer();

        var response = mvc.perform(delete("/api/admin/members/99").header(AUTHORIZATION, authorization))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("Request could not be processed"))
                .andReturn().getResponse();
        assertThat(response.getContentAsString()).doesNotContain(EMAIL, authorization, "fixture-private-database-detail");
        verifyNoInteractions(adminMembers);

        mvc.perform(delete("/api/admin/members/99").header(AUTHORIZATION, authorization))
                .andExpect(status().isNoContent());
        verify(adminMembers).deleteMember(99L);
        verify(memberReader, times(2)).isCurrentIdentity(MEMBER_ID, EMAIL, COMPANY_ID, MemberRole.ADMIN);
    }

    @Test
    void 비정상_회원_PK는_DB_조회_전에_거부한다() throws Exception {
        String token = tokens.generateAccessToken(EMAIL, Map.of("memberId", 0L,
                "companyId", COMPANY_ID, "roles", List.of("ADMIN")));
        mvc.perform(delete("/api/admin/members/99").header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(memberReader, adminMembers);
    }

    private String bearer() {
        return "Bearer " + tokens.generateAccessToken(EMAIL, Map.of("memberId", MEMBER_ID,
                "companyId", COMPANY_ID, "roles", List.of("ADMIN")));
    }
}
