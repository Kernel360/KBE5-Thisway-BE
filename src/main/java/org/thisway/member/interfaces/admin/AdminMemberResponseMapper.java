package org.thisway.member.interfaces.admin;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.thisway.member.domain.MemberInfo;
import org.thisway.support.common.PageInfo;

import java.util.List;

@Component
public class AdminMemberResponseMapper {

    public AdminMemberResponse.MemberDetailResponse toMemberDetailResponse(MemberInfo.MemberWithCompany memberWithCompany) {
        return AdminMemberResponse.MemberDetailResponse.builder()
                .id(memberWithCompany.id())
                .companyName(memberWithCompany.companyInfo().name())
                .role(memberWithCompany.role().name())
                .name(memberWithCompany.name())
                .email(memberWithCompany.email())
                .phone(memberWithCompany.phone())
                .memo(memberWithCompany.memo())
                .build();
    }

    public AdminMemberResponse.MembersResponse toMembersResponse(Page<MemberInfo.MemberWithCompany> info) {
        List<AdminMemberResponse.MemberDetailResponse> members = info.get()
                .map(this::toMemberDetailResponse)
                .toList();
        PageInfo pageInfo = PageInfo.from(info);

        return AdminMemberResponse.MembersResponse.builder()
                .members(members)
                .pageInfo(pageInfo)
                .build();
    }
}
