package org.thisway.member.controller.dto.response;

import org.thisway.member.service.dto.output.CompanyChefMemberSummaryOutput;

public record CompanyChefMemberSummaryResponse(
        long companyChefCount,
        long companyAdminCount,
        long memberCount
) {

    public static CompanyChefMemberSummaryResponse from(CompanyChefMemberSummaryOutput memberSummary) {
        return new CompanyChefMemberSummaryResponse(
                memberSummary.companyChefCount(),
                memberSummary.companyAdminCount(),
                memberSummary.memberCount()
        );
    }
}
