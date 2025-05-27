package org.thisway.company.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

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
}
