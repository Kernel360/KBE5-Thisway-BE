package org.thisway.member.controller;

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
import org.thisway.member.dto.request.CompanyChefMemberRegisterRequest;
import org.thisway.member.dto.request.CompanyChefMemberUpdateRequest;
import org.thisway.member.dto.response.CompanyChefMemberDetailResponse;
import org.thisway.member.dto.response.CompanyChefMemberSummaryResponse;
import org.thisway.member.dto.response.CompanyChefMembersResponse;
import org.thisway.member.service.CompanyChefMemberService;

@RestController
@RequestMapping("/api/company-chef/members")
@RequiredArgsConstructor
public class CompanyChefMemberController {

    private final CompanyChefMemberService companyChefMemberService;

    @GetMapping("/{id}")
    public ResponseEntity<CompanyChefMemberDetailResponse> getMemberDetail(@PathVariable Long id) {
        CompanyChefMemberDetailResponse response = CompanyChefMemberDetailResponse.from(
                companyChefMemberService.getMemberDetail(id));

        return ResponseEntity.status(HttpStatus.OK)
                .body(response);
    }

    @GetMapping
    public ResponseEntity<CompanyChefMembersResponse> getMembers(
            @PageableDefault Pageable pageable
    ) {
        CompanyChefMembersResponse response = CompanyChefMembersResponse.from(
                companyChefMemberService.getMembers(pageable));

        return ResponseEntity.status(HttpStatus.OK)
                .body(response);
    }

    @PostMapping
    public ResponseEntity<Void> registerMember(
            @RequestBody @Validated CompanyChefMemberRegisterRequest request
    ) {
        companyChefMemberService.registerMember(request.toMemberRegisterInput());

        return ResponseEntity.status(HttpStatus.CREATED)
                .build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> updateMember(
            @PathVariable long id,
            @RequestBody @Validated CompanyChefMemberUpdateRequest request
    ) {
        companyChefMemberService.updateMember(request.toMemberUpdateInput(id));

        return ResponseEntity.status(HttpStatus.OK)
                .build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMember(@PathVariable Long id) {
        companyChefMemberService.deleteMember(id);

        return ResponseEntity.status(HttpStatus.NO_CONTENT)
                .build();
    }

    @GetMapping("/summary")
    public ResponseEntity<CompanyChefMemberSummaryResponse> summary() {
        CompanyChefMemberSummaryResponse response = CompanyChefMemberSummaryResponse.from(
                companyChefMemberService.summary()
        );

        return ResponseEntity.status(HttpStatus.OK)
                .body(response);
    }
}
