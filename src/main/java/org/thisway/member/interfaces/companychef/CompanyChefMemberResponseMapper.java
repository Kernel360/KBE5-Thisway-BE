package org.thisway.member.interfaces.companychef;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.thisway.member.domain.MemberInfo;
import org.thisway.support.common.PageInfo;

import java.util.List;

@Component
public class CompanyChefMemberResponseMapper {

    public CompanyChefMemberResponse.MemberDetailResponse toMemberDetailResponse(MemberInfo.MemberWithCompany memberWithCompany) {
        return CompanyChefMemberResponse.MemberDetailResponse.builder()
                .id(memberWithCompany.id())
                .role(memberWithCompany.role())
                .name(memberWithCompany.name())
                .email(memberWithCompany.email())
                .phone(memberWithCompany.phone())
                .memo(memberWithCompany.memo())
                .build();
    }

    public CompanyChefMemberResponse.MembersResponse toMembersResponse(Page<MemberInfo.MemberWithCompany> info) {
        List<CompanyChefMemberResponse.MemberDetailResponse> members = info.get()
                .map(this::toMemberDetailResponse)
                .toList();
        PageInfo pageInfo = PageInfo.from(info);

        return CompanyChefMemberResponse.MembersResponse.builder()
                .members(members)
                .pageInfo(pageInfo)
                .build();
    }

    public CompanyChefMemberResponse.MemberSummaryResponse toMemberSummaryResponse(MemberInfo.MemberSummary info) {
        return CompanyChefMemberResponse.MemberSummaryResponse.builder()
                .memberCount(info.memberCount())
                .companyChefCount(info.companyChefCount())
                .companyAdminCount(info.companyAdminCount())
                .build();
    }
}
