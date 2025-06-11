package org.thisway.company.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.thisway.company.dto.AdminCompanyUpdateInput;

public record AdminCompanyUpdateRequest(

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
        String memo
) {

    public AdminCompanyUpdateInput toCompanyUpdateInput(long id) {
        return AdminCompanyUpdateInput.builder()
                .id(id)
                .name(name)
                .crn(crn)
                .contact(contact)
                .addrRoad(addrRoad)
                .addrDetail(addrDetail)
                .memo(memo)
                .build();
    }
}
