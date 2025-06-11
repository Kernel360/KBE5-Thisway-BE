package org.thisway.company.dto;


import java.util.List;
import org.springframework.data.domain.Page;
import org.thisway.common.PageInfo;
import org.thisway.company.entity.Company;

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
