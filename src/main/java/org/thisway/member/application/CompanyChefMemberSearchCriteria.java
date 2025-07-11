package org.thisway.member.application;

import lombok.Builder;

@Builder
public record CompanyChefMemberSearchCriteria(
        String memberName
) {
}
