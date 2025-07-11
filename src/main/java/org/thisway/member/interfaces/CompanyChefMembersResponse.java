package org.thisway.member.interfaces;

import java.util.List;

import org.thisway.support.common.PageInfo;
import org.thisway.member.application.CompanyChefMembersOutput;

public record CompanyChefMembersResponse(
        List<CompanyChefMemberDetailResponse> members,

        PageInfo pageInfo
) {

    public static CompanyChefMembersResponse from(CompanyChefMembersOutput membersOutput) {
        List<CompanyChefMemberDetailResponse> members = membersOutput.members().stream()
                .map(CompanyChefMemberDetailResponse::from)
                .toList();
        return new CompanyChefMembersResponse(members, membersOutput.pageInfo());
    }
}
