package org.thisway.company.dto.response;


import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.domain.Page;
import org.thisway.company.entity.Company;

public record CompaniesResponse(
        @JsonProperty(value = "companies")
        Page<CompanyResponse> companyResponses
) {

    public static CompaniesResponse from(Page<Company> companies) {
        return new CompaniesResponse(companies.map(CompanyResponse::from));
    }
}
