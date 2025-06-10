package org.thisway.member.dto;

import lombok.Builder;

@Builder
public record CompanyChefMemberSummaryOutput(
        long companyChefCount,
        long companyAdminCount,
        long memberCount
) {
}
