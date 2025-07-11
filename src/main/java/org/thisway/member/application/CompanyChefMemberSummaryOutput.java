package org.thisway.member.application;

import lombok.Builder;

@Builder
public record CompanyChefMemberSummaryOutput(
        long companyChefCount,
        long companyAdminCount,
        long memberCount
) {
}
