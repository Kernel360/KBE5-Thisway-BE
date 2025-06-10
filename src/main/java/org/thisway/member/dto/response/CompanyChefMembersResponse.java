package org.thisway.member.dto.response;

import java.util.List;
import org.thisway.common.PageInfo;
import org.thisway.member.dto.CompanyChefMembersOutput;

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
