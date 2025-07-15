package org.thisway.member.interfaces.companychef;

import lombok.Builder;
import org.thisway.member.domain.MemberRole;
import org.thisway.support.common.PageInfo;

import java.util.List;

public class CompanyChefMemberResponse {

    @Builder
    public record MemberDetailResponse(
            Long id,
            MemberRole role,
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

    @Builder
    public record MemberSummaryResponse(
            long companyChefCount,
            long companyAdminCount,
            long memberCount
    ) {
    }
}
