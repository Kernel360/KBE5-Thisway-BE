package org.thisway.company.dto.response;

import org.thisway.company.entity.Company;

public record CompanyResponse(
        Long id,
        String name,
        String crn,
        String contact,
        String addrRoad,
        String addrDetail,
        String memo,
        Integer gpsCycle
) {

    public static CompanyResponse from(Company company) {
        return new CompanyResponse(
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
