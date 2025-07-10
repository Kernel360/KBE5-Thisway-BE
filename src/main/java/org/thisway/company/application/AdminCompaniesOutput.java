package org.thisway.company.application;


import org.springframework.data.domain.Page;
import org.thisway.common.PageInfo;
import org.thisway.company.domain.Company;

import java.util.List;

public record AdminCompaniesOutput(
        List<AdminCompanyDetailOutput> companies,

        PageInfo pageInfo
) {

    public static AdminCompaniesOutput from(Page<Company> companyPage) {
        List<AdminCompanyDetailOutput> companies = companyPage.map(AdminCompanyDetailOutput::from).toList();
        PageInfo pageInfo = PageInfo.from(companyPage);

        return new AdminCompaniesOutput(companies, pageInfo);
    }
}
