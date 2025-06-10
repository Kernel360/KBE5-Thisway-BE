package org.thisway.company.dto;

import lombok.Builder;

@Builder
public record AdminCompanyUpdateInput(
        Long id,
        String name,
        String crn,
        String contact,
        String addrRoad,
        String addrDetail,
        String memo
) {
}
