package org.thisway.member.interfaces;

import org.thisway.member.application.CompanyChefMemberSummaryOutput;

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
