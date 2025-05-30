package org.thisway.company.support;

import org.thisway.company.entity.Company;

public class CompanyFixture {

    public static Company createCompany() {
        return Company.builder()
                .name("name")
                .crn("crn")
                .contact("contact")
                .addrRoad("addrRoad")
                .addrDetail("addrDetail")
                .memo("memo")
                .gpsCycle(60)
                .build();
    }
}
