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
import org.thisway.company.controller.dto.request.AdminCompanyRegisterRequest;
import org.thisway.company.controller.dto.request.AdminCompanyUpdateRequest;
import org.thisway.company.controller.dto.response.AdminCompaniesResponse;
import org.thisway.company.controller.dto.response.AdminCompanyDetailResponse;
import org.thisway.company.service.AdminCompanyService;

@RestController
@RequestMapping("/api/admin/companies")
@RequiredArgsConstructor
public class AdminCompanyController {

    private final AdminCompanyService adminCompanyService;

    @GetMapping("/{id}")
    public ResponseEntity<AdminCompanyDetailResponse> getCompanyDetail(@PathVariable Long id) {
        AdminCompanyDetailResponse response = AdminCompanyDetailResponse.from(
                adminCompanyService.getCompanyDetail(id)
        );

        return ResponseEntity.status(HttpStatus.OK)
                .body(response);
    }

    @GetMapping
    public ResponseEntity<AdminCompaniesResponse> getCompanies(@PageableDefault Pageable pageable) {
        AdminCompaniesResponse response = AdminCompaniesResponse.from(
                adminCompanyService.getCompanies(pageable)
        );

        return ResponseEntity.status(HttpStatus.OK)
                .body(response);
    }

    @PostMapping
    public ResponseEntity<Void> registerCompany(@RequestBody @Validated AdminCompanyRegisterRequest request) {
        adminCompanyService.registerCompany(request.toCompanyRegisterInput());
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
