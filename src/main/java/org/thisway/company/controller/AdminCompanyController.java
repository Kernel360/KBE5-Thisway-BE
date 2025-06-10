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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.thisway.company.dto.request.AdminCompanyRegisterRequest;
import org.thisway.company.dto.request.AdminCompanyUpdateRequest;
import org.thisway.company.dto.response.AdminCompaniesResponse;
import org.thisway.company.dto.response.AdminCompanyResponse;
import org.thisway.company.service.AdminCompanyService;

@RestController
@RequestMapping("/api/admin/companies")
@RequiredArgsConstructor
public class AdminCompanyController {

    private final AdminCompanyService adminCompanyService;

    @GetMapping("/{id}")
    public ResponseEntity<AdminCompanyResponse> getCompanyDetail(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(adminCompanyService.getCompanyDetail(id));
    }

    @GetMapping
    public ResponseEntity<AdminCompaniesResponse> getCompanies(@PageableDefault Pageable pageable) {
        return ResponseEntity.ok(adminCompanyService.getCompanies(pageable));
    }

    @PostMapping
    public ResponseEntity<Void> registerCompany(@RequestBody @Validated AdminCompanyRegisterRequest request) {
        adminCompanyService.registerCompany(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> updateCompany(
            @PathVariable long id,
            @RequestBody @Validated AdminCompanyUpdateRequest request
    ) {
        adminCompanyService.updateCompany(request.toCompanyUpdateInput(id));

        return ResponseEntity.status(HttpStatus.OK)
                .build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCompany(@PathVariable Long id) {
        adminCompanyService.deleteCompany(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT)
                .build();
    }
}
