package org.thisway.company.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.thisway.company.entity.Company;

public record AdminCompanyRegisterRequest(
        @NotBlank
        String name,

        @NotBlank
        String crn,

        @NotBlank
        String contact,

        @NotBlank
        String addrRoad,

        @NotBlank
        String addrDetail,

        @NotNull
        String memo,

        @Positive
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
