package org.thisway.company.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.thisway.common.ApiResponse;
import org.thisway.company.dto.request.CompanyRegisterRequest;
import org.thisway.company.dto.response.CompaniesResponse;
import org.thisway.company.dto.response.CompanyResponse;
import org.thisway.company.service.CompanyService;

@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;

    @GetMapping("/{id}")
    public ApiResponse<CompanyResponse> getCompanyDetail(@PathVariable Long id) {
        return ApiResponse.ok(companyService.getCompanyDetail(id));
    }

    @GetMapping
    public ApiResponse<CompaniesResponse> getCompanies(@PageableDefault Pageable pageable) {
        return ApiResponse.ok(companyService.getCompanies(pageable));
    }

    @PostMapping
    public ApiResponse<Void> registerCompany(@RequestBody @Validated CompanyRegisterRequest request) {
        companyService.registerCompany(request);
        return ApiResponse.created();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteCompany(@PathVariable Long id) {
        companyService.deleteCompany(id);
        return ApiResponse.noContent();
    }
}
