package org.thisway.member.controller.dto.request;

import org.thisway.member.service.dto.CompanyChefMemberSearchCriteria;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CompanyChefMemberSearchRequest {

    private String memberName;

    public CompanyChefMemberSearchCriteria toCriteria() {
        return CompanyChefMemberSearchCriteria.builder()
            .memberName(memberName)
            .build();
    }
}
