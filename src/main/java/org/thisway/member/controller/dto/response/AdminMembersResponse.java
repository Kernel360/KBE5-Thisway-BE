package org.thisway.member.controller.dto.response;

import java.util.List;
import org.thisway.common.PageInfo;
import org.thisway.member.service.dto.output.AdminMembersOutput;

public record AdminMembersResponse(
        List<AdminMemberDetailResponse> members,

        PageInfo pageInfo
) {

    public static AdminMembersResponse from(AdminMembersOutput adminMembersOutput) {
        List<AdminMemberDetailResponse> members = adminMembersOutput.members().stream()
                .map(AdminMemberDetailResponse::from)
                .toList();
        return new AdminMembersResponse(members, adminMembersOutput.pageInfo());
    }
}
