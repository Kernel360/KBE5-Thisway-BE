package org.thisway.company.dto.response;


import java.util.List;
import org.thisway.common.PageInfo;
import org.thisway.company.dto.AdminCompaniesOutput;

public record AdminCompaniesResponse(
        List<AdminCompanyDetailResponse> companies,

        PageInfo pageInfo
) {

    public static AdminCompaniesResponse from(AdminCompaniesOutput companiesOutput) {
        List<AdminCompanyDetailResponse> companies = companiesOutput.companies().stream()
                .map(AdminCompanyDetailResponse::from)
                .toList();
        return new AdminCompaniesResponse(companies, companiesOutput.pageInfo());
    }
}
