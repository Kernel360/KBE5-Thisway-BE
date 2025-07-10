package org.thisway.member.service.dto;

import lombok.Builder;

@Builder
public record CompanyChefMemberSearchCriteria(
        String memberName
) {
}
