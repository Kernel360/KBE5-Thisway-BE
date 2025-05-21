package org.thisway.company.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.thisway.company.dto.response.CompanyResponse;
import org.thisway.company.entity.Company;

public record CompanyRegisterRequest(
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

    public static CompanyResponse from(Company company) {
        return new CompanyResponse(
                company.getName(),
                company.getCrn(),
                company.getContact(),
                company.getAddrRoad(),
                company.getAddrDetail(),
                company.getMemo(),
                company.getGpsCycle()
        );
    }
}
