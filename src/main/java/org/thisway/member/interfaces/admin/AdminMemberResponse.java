package org.thisway.member.interfaces.admin;

import lombok.Builder;
import org.thisway.support.common.PageInfo;

import java.util.List;

public class AdminMemberResponse {

    @Builder
    public record MemberDetailResponse(
            long id,
            String companyName,
            String role,
            String name,
            String email,
            String phone,
            String memo
    ) {
    }

    @Builder
    public record MembersResponse(
            List<MemberDetailResponse> members,
            PageInfo pageInfo
    ) {
    }
}
