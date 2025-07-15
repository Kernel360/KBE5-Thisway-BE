package org.thisway.member.interfaces.companychef;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.thisway.member.application.MemberService;
import org.thisway.member.domain.MemberCommand;
import org.thisway.member.domain.MemberInfo;
import org.thisway.member.domain.MemberQuery;
import org.thisway.support.security.dto.request.MemberDetails;

@RestController
@RequestMapping("/api/company-chef/members")
@RequiredArgsConstructor
public class CompanyChefMemberController {

    private final MemberService memberService;
    private final CompanyChefMemberRequestMapper companyChefMemberRequestMapper;
    private final CompanyChefMemberResponseMapper companyChefMemberResponseMapper;


    @GetMapping("/{id}")
    public ResponseEntity<CompanyChefMemberResponse.MemberDetailResponse> getMemberDetail(
            @AuthenticationPrincipal MemberDetails memberDetails,
            @PathVariable Long id
    ) {
        MemberInfo.MemberWithCompany member = memberService.retrieveMemberWithCompany(id, memberDetails.getUsername());
        CompanyChefMemberResponse.MemberDetailResponse response = companyChefMemberResponseMapper.toMemberDetailResponse(member);

        return ResponseEntity.status(HttpStatus.OK)
                .body(response);
    }

    @GetMapping
    public ResponseEntity<CompanyChefMemberResponse.MembersResponse> getMembers(
            @AuthenticationPrincipal MemberDetails memberDetails,
            @ModelAttribute CompanyChefMemberRequest.MemberSearchRequest search,
            @PageableDefault(
                    sort = "createdAt",
                    direction = Sort.Direction.DESC
            ) Pageable pageable
    ) {
        MemberQuery.SearchMember searchQuery = companyChefMemberRequestMapper.from(search, memberDetails.getCompanyId());
        Page<MemberInfo.MemberWithCompany> members = memberService.retrieveMembersWithCompany(searchQuery, memberDetails.getUsername(), pageable);
        CompanyChefMemberResponse.MembersResponse response = companyChefMemberResponseMapper.toMembersResponse(members);

        return ResponseEntity.status(HttpStatus.OK)
                .body(response);
    }

    @PostMapping
    public ResponseEntity<Void> registerMember(
            @AuthenticationPrincipal MemberDetails memberDetails,
            @RequestBody @Validated CompanyChefMemberRequest.MemberRegisterRequest request
    ) {
        MemberCommand.RegisterMember member = companyChefMemberRequestMapper.from(request, memberDetails.getCompanyId());
        memberService.registerMember(member, memberDetails.getUsername());

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> updateMember(
            @PathVariable long id,
            @RequestBody @Validated CompanyChefMemberRequest.MemberUpdateRequest request,
            @AuthenticationPrincipal MemberDetails memberDetails
    ) {
        MemberCommand.UpdateMember member = companyChefMemberRequestMapper.from(id, request);
        memberService.updateMember(member, memberDetails.getUsername());

        return ResponseEntity.status(HttpStatus.OK).build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMember(
            @AuthenticationPrincipal MemberDetails memberDetails,
            @PathVariable Long id
    ) {
        memberService.deleteMember(id, memberDetails.getUsername());

        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @GetMapping("/summary")
    public ResponseEntity<CompanyChefMemberResponse.MemberSummaryResponse> summary(
            @AuthenticationPrincipal MemberDetails memberDetails
    ) {

        MemberInfo.MemberSummary summary = memberService.summaryCompanyMember(memberDetails.getCompanyId(), memberDetails.getUsername());
        CompanyChefMemberResponse.MemberSummaryResponse response = companyChefMemberResponseMapper.toMemberSummaryResponse(summary);

        return ResponseEntity.status(HttpStatus.OK)
                .body(response);
    }
}
