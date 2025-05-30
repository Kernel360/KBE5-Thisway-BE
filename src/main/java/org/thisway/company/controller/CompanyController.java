package org.thisway.company.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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
    public ResponseEntity<CompanyResponse> getCompanyDetail(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(companyService.getCompanyDetail(id));
    }

    @GetMapping
    public ResponseEntity<CompaniesResponse> getCompanies(@PageableDefault Pageable pageable) {
        return ResponseEntity.ok(companyService.getCompanies(pageable));
    }

    @PostMapping
    public ResponseEntity<Void> registerCompany(@RequestBody @Validated CompanyRegisterRequest request) {
        companyService.registerCompany(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCompany(@PathVariable Long id) {
        companyService.deleteCompany(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT)
                .build();
    }
}
