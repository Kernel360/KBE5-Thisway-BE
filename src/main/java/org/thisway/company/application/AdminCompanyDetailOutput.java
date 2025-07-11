package org.thisway.company.application;

import org.thisway.company.domain.Company;

public record AdminCompanyDetailOutput(
        Long id,
        String name,
        String crn,
        String contact,
        String addrRoad,
        String addrDetail,
        String memo,
        Integer gpsCycle
) {

    public static AdminCompanyDetailOutput from(Company company) {
        return new AdminCompanyDetailOutput(
                company.getId(),
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
