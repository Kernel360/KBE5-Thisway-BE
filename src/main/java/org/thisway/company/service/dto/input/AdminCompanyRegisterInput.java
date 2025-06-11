package org.thisway.company.service.dto.input;

import lombok.Builder;
import org.thisway.company.entity.Company;

@Builder
public record AdminCompanyRegisterInput(
        String name,
        String crn,
        String contact,
        String addrRoad,
        String addrDetail,
        String memo,
        Integer gpsCycle
) {

    public Company toCompany() {
        return Company.builder()
                .name(name)
                .crn(crn)
                .contact(contact)
                .addrRoad(addrRoad)
                .addrDetail(addrDetail)
                .memo(memo)
                .gpsCycle(gpsCycle)
                .build();
    }
}
