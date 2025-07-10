package org.thisway.company.interfaces;

import org.thisway.company.application.AdminCompanyDetailOutput;

public record AdminCompanyDetailResponse(
        Long id,
        String name,
        String crn,
        String contact,
        String addrRoad,
        String addrDetail,
        String memo,
        Integer gpsCycle
) {

    public static AdminCompanyDetailResponse from(AdminCompanyDetailOutput company) {
        return new AdminCompanyDetailResponse(
                company.id(),
                company.name(),
                company.crn(),
                company.contact(),
                company.addrRoad(),
                company.addrDetail(),
                company.memo(),
                company.gpsCycle()
        );
    }
}
