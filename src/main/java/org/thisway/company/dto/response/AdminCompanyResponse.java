package org.thisway.company.dto.response;

import org.thisway.company.entity.Company;

public record AdminCompanyResponse(
        Long id,
        String name,
        String crn,
        String contact,
        String addrRoad,
        String addrDetail,
        String memo,
        Integer gpsCycle
) {

    public static AdminCompanyResponse from(Company company) {
        return new AdminCompanyResponse(
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
