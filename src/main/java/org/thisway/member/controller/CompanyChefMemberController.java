package org.thisway.member.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.thisway.member.dto.response.CompanyChefMemberDetailResponse;
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
}
