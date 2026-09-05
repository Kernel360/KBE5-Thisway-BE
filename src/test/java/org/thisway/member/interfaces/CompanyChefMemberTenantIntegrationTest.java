package org.thisway.member.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.company.domain.Company;
import org.thisway.company.infrastructure.CompanyRepository;
import org.thisway.member.domain.Member;
import org.thisway.member.domain.MemberRole;
import org.thisway.member.infrastructure.MemberRepository;
import org.thisway.support.common.ErrorCode;
import org.thisway.support.security.utils.JwtTokenProvider;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CompanyChefMemberTenantIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private MemberRepository memberRepository;

    private Company companyA;
    private Company companyB;
    private Member companyBMember;
    private String companyAToken;

    @BeforeEach
    void setUp() {
        memberRepository.deleteAll();
        companyRepository.deleteAll();

        companyA = companyRepository.save(company("company-a", "crn-a"));
        companyB = companyRepository.save(company("company-b", "crn-b"));
        companyBMember = memberRepository.save(member(companyB, "member-b@example.com"));
        companyAToken = accessToken(companyA.getId());
    }

    @Test
    void repository는_memberId와_companyId를_함께_만족할_때만_반환한다() {
        assertThat(memberRepository.findByIdAndCompanyIdAndActiveTrue(
                companyBMember.getId(), companyB.getId()))
                .contains(companyBMember);
        assertThat(memberRepository.findByIdAndCompanyIdAndActiveTrue(
                companyBMember.getId(), companyA.getId()))
                .isEmpty();
    }

    @Test
    void 자기_업체_멤버는_상세_조회할_수_있다() throws Exception {
        Member companyAMember = memberRepository.save(member(companyA, "member-a@example.com"));

        mockMvc.perform(get("/api/company-chef/members/{id}", companyAMember.getId())
                        .header(AUTHORIZATION, bearer(companyAToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(companyAMember.getId()));
    }

    @Test
    void 다른_업체_멤버_상세_조회는_존재를_숨기고_404를_반환한다() throws Exception {
        assertCrossTenantNotFound(get("/api/company-chef/members/{id}", companyBMember.getId()));
    }

    @Test
    void 다른_업체_멤버_수정은_404이고_데이터를_바꾸지_않는다() throws Exception {
        mockMvc.perform(put("/api/company-chef/members/{id}", companyBMember.getId())
                        .header(AUTHORIZATION, bearer(companyAToken))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "attacker-updated-name",
                                  "email": "attacker-updated@example.com",
                                  "phone": "01099999999",
                                  "memo": "attacker-updated-memo"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.MEMBER_NOT_FOUND.getCode()));

        Member unchangedMember = memberRepository.findById(companyBMember.getId()).orElseThrow();
        assertThat(unchangedMember.getName()).isEqualTo("member-b");
        assertThat(unchangedMember.getEmail()).isEqualTo("member-b@example.com");
    }

    @Test
    void 다른_업체_멤버_삭제는_404이고_active를_유지한다() throws Exception {
        assertCrossTenantNotFound(delete("/api/company-chef/members/{id}", companyBMember.getId()));

        assertThat(memberRepository.findById(companyBMember.getId()).orElseThrow().isActive()).isTrue();
    }

    private void assertCrossTenantNotFound(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request
    ) throws Exception {
        mockMvc.perform(request.header(AUTHORIZATION, bearer(companyAToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.MEMBER_NOT_FOUND.getCode()));
    }

    private String accessToken(long companyId) {
        return jwtTokenProvider.generateAccessToken("company-chef@example.com", Map.of(
                "roles", List.of(MemberRole.COMPANY_CHEF.name()),
                "companyId", companyId
        ));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private Company company(String name, String crn) {
        return Company.builder()
                .name(name)
                .crn(crn)
                .contact("01000000000")
                .addrRoad("road")
                .addrDetail("detail")
                .memo("memo")
                .gpsCycle(60)
                .build();
    }

    private Member member(Company company, String email) {
        return Member.builder()
                .company(company)
                .role(MemberRole.MEMBER)
                .name(company.getName().replace("company", "member"))
                .email(email)
                .password("Password123!")
                .phone("01012345678")
                .memo("memo")
                .build();
    }
}
