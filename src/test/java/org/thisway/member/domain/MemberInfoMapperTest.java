package org.thisway.member.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class MemberInfoMapperTest {

    private MemberInfoMapper memberInfoMapper = new MemberInfoMapper();

    @Test
    @DisplayName("멤버와 회사정보가 올바르게 매핑된다")
    void 멤버와_회사정보가_올바르게_매핑된다() {
        // given
        Member member1 = mock(Member.class);
        Member member2 = mock(Member.class);

        Pageable pageable = PageRequest.of(0, 10);
        Page<Member> members = new PageImpl<>(Arrays.asList(member1, member2), pageable, 2);

        CompanyInfo company1 = CompanyInfo.builder()
                .id(100L)
                .build();

        CompanyInfo company2 = CompanyInfo.builder()
                .id(200L)
                .build();

        List<CompanyInfo> companies = Arrays.asList(company1, company2);

        given(member1.getCompanyId()).willReturn(100L);
        given(member2.getCompanyId()).willReturn(200L);

        // when
        Page<MemberInfo.MemberWithCompany> result = memberInfoMapper.toMemberInfos(members, companies);

        // then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getNumber()).isEqualTo(0);
        assertThat(result.getSize()).isEqualTo(10);

        MemberInfo.MemberWithCompany memberInfo1 = result.getContent().get(0);
        assertThat(memberInfo1.companyInfo().id()).isEqualTo(100L);

        MemberInfo.MemberWithCompany memberInfo2 = result.getContent().get(1);
        assertThat(memberInfo2.companyInfo().id()).isEqualTo(200L);
    }

    @Test
    @DisplayName("회사정보가 없는 멤버는 예외가 발생한다")
    void 회사정보가_없는_멤버는_예외가_발생한다() {
        // given
        long notFoundCompanyId = 999L;
        Member member = mock(Member.class);

        Pageable pageable = PageRequest.of(0, 10);
        Page<Member> members = new PageImpl<>(List.of(member), pageable, 1);

        CompanyInfo company = CompanyInfo.builder()
                .id(100L)
                .build();

        List<CompanyInfo> companies = List.of(company);

        given(member.getCompanyId()).willReturn(notFoundCompanyId);

        // when & then
        assertThatThrownBy(() -> memberInfoMapper.toMemberInfos(members, companies))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
