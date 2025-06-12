package org.thisway.company.controller.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.thisway.company.service.dto.input.AdminCompanyRegisterInput;

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

    public AdminCompanyRegisterInput toCompanyRegisterInput() {
        return AdminCompanyRegisterInput.builder()
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
