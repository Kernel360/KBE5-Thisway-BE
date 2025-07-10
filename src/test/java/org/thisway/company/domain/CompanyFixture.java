package org.thisway.company.domain;

import org.thisway.company.domain.Company;

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
