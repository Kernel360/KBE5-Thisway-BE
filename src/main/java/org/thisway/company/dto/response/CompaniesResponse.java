package org.thisway.company.dto.response;


import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.springframework.data.domain.Page;
import org.thisway.common.PageInfo;
import org.thisway.company.entity.Company;

public record CompaniesResponse(
        @JsonProperty(value = "companies")
        List<CompanyResponse> companyResponses,

        PageInfo pageInfo
) {

    public static CompaniesResponse from(Page<Company> companies) {
        List<CompanyResponse> companyResponse = companies.map(CompanyResponse::from).toList();
        PageInfo pageInfo = PageInfo.from(companies);

        return new CompaniesResponse(companyResponse, pageInfo);
    }
}
