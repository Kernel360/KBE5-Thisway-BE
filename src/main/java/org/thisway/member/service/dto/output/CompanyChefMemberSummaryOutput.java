package org.thisway.member.service.dto.output;

import lombok.Builder;

@Builder
public record CompanyChefMemberSummaryOutput(
        long companyChefCount,
        long companyAdminCount,
        long memberCount
) {
}
