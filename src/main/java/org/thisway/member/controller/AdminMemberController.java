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
import org.thisway.member.controller.dto.request.AdminMemberRegisterRequest;
import org.thisway.member.controller.dto.request.AdminMemberUpdateRequest;
import org.thisway.member.controller.dto.response.AdminMemberDetailResponse;
import org.thisway.member.controller.dto.response.AdminMembersResponse;
import org.thisway.member.service.AdminMemberService;

@RestController
@RequestMapping("/api/admin/members")
@RequiredArgsConstructor
public class AdminMemberController {

    private final AdminMemberService adminMemberService;

    @GetMapping("/{id}")
    public ResponseEntity<AdminMemberDetailResponse> getMemberDetail(@PathVariable Long id) {
        AdminMemberDetailResponse response = AdminMemberDetailResponse.from(adminMemberService.getMemberDetail(id));

        return ResponseEntity.status(HttpStatus.OK)
                .body(response);
    }

    @GetMapping
    public ResponseEntity<AdminMembersResponse> getMembers(
            @PageableDefault Pageable pageable
    ) {
        AdminMembersResponse response = AdminMembersResponse.from(adminMemberService.getMembers(pageable));

        return ResponseEntity.status(HttpStatus.OK)
                .body(response);
    }

    @PostMapping
    public ResponseEntity<Void> registerMember(
            @RequestBody @Validated AdminMemberRegisterRequest request
    ) {
        adminMemberService.registerMember(request.toMemberRegisterInput());

        return ResponseEntity.status(HttpStatus.CREATED)
                .build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> updateMember(
            @PathVariable long id,
            @RequestBody @Validated AdminMemberUpdateRequest request
    ) {
        adminMemberService.updateMember(request.toMemberUpdateInput(id));

        return ResponseEntity.status(HttpStatus.OK)
                .build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMember(@PathVariable Long id) {
        adminMemberService.deleteMember(id);

        return ResponseEntity.status(HttpStatus.NO_CONTENT)
                .build();
    }
}
