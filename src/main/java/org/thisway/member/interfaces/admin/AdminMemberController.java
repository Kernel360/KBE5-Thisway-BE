package org.thisway.member.interfaces.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.thisway.member.application.MemberService;
import org.thisway.member.domain.MemberCommand;
import org.thisway.member.domain.MemberInfo;
import org.thisway.support.security.dto.request.MemberDetails;

@RestController
@RequestMapping("/api/admin/members")
@RequiredArgsConstructor
public class AdminMemberController {

    private final MemberService memberService;
    private final AdminMemberRequestMapper adminMemberRequestMapper;
    private final AdminMemberResponseMapper adminMemberResponseMapper;

    @PostMapping
    public ResponseEntity<Void> registerMember(
            @RequestBody @Validated AdminMemberRequest.MemberRegisterRequest request,
            @AuthenticationPrincipal MemberDetails memberDetails
    ) {
        MemberCommand.RegisterMember command = adminMemberRequestMapper.from(request);
        memberService.registerMember(command, memberDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminMemberResponse.MemberDetailResponse> getMemberDetail(
            @PathVariable Long id,
            @AuthenticationPrincipal MemberDetails memberDetails
    ) {
        MemberInfo.MemberWithCompany member = memberService.retrieveMemberWithCompany(id, memberDetails.getUsername());
        AdminMemberResponse.MemberDetailResponse response = adminMemberResponseMapper.toMemberDetailResponse(member);

        return ResponseEntity.status(HttpStatus.OK)
                .body(response);
    }

    @GetMapping
    public ResponseEntity<AdminMemberResponse.MembersResponse> getMembers(
            @PageableDefault Pageable pageable,
            @AuthenticationPrincipal MemberDetails memberDetails
    ) {
        Page<MemberInfo.MemberWithCompany> members = memberService.retrieveMembersWithCompany(memberDetails.getUsername(), pageable);
        AdminMemberResponse.MembersResponse response = adminMemberResponseMapper.toMembersResponse(members);

        return ResponseEntity.status(HttpStatus.OK)
                .body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> updateMember(
            @PathVariable long id,
            @RequestBody @Validated AdminMemberRequest.MemberUpdateRequest request,
            @AuthenticationPrincipal MemberDetails memberDetails
    ) {
        MemberCommand.UpdateMember updateCommand = adminMemberRequestMapper.from(request, id);
        memberService.updateMember(updateCommand, memberDetails.getUsername());

        return ResponseEntity.status(HttpStatus.OK)
                .build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMember(
            @PathVariable Long id,
            @AuthenticationPrincipal MemberDetails memberDetails
    ) {
        memberService.deleteMember(id, memberDetails.getUsername());

        return ResponseEntity.status(HttpStatus.NO_CONTENT)
                .build();
    }
}
