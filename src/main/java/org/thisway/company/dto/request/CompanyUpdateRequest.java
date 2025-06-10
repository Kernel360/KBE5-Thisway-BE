package org.thisway.company.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.thisway.company.dto.CompanyUpdateInput;

public record CompanyUpdateRequest(

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

    public CompanyUpdateInput toCompanyUpdateInput(long id) {
        return CompanyUpdateInput.builder()
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
