package org.thisway.company.dto.response;


import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.springframework.data.domain.Page;
import org.thisway.common.PageInfo;
import org.thisway.company.entity.Company;

public record AdminCompaniesResponse(
        @JsonProperty(value = "companies")
        List<AdminCompanyResponse> adminCompanyRespons,

        PageInfo pageInfo
) {

    public static AdminCompaniesResponse from(Page<Company> companies) {
        List<AdminCompanyResponse> adminCompanyResponse = companies.map(AdminCompanyResponse::from).toList();
        PageInfo pageInfo = PageInfo.from(companies);

        return new AdminCompaniesResponse(adminCompanyResponse, pageInfo);
    }
}
