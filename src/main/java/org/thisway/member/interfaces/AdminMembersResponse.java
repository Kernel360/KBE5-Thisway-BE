package org.thisway.member.interfaces;

import java.util.List;

import org.thisway.support.common.PageInfo;
import org.thisway.member.application.AdminMembersOutput;

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
