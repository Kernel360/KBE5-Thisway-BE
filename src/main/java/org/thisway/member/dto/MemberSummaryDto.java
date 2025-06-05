package org.thisway.member.dto;

import lombok.Builder;

@Builder
public record MemberSummaryDto(
        long companyAdminCount,
        long companyChefCount,
        long memberCount
) {
}
