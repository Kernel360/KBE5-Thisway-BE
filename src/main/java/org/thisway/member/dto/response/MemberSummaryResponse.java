package org.thisway.member.dto.response;

import org.thisway.member.dto.MemberSummaryDto;

public record MemberSummaryResponse(
        long companyChefCount,
        long companyAdminCount,
        long memberCount
) {

    public static MemberSummaryResponse from(MemberSummaryDto memberSummary) {
        return new MemberSummaryResponse(
                memberSummary.companyChefCount(),
                memberSummary.companyAdminCount(),
                memberSummary.memberCount()
        );
    }
}
