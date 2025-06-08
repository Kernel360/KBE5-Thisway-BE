package org.thisway.member.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.thisway.member.dto.request.AdminMemberRegisterRequest;
import org.thisway.member.dto.response.MembersResponse;
import org.thisway.member.service.AdminMemberService;

@RestController
@RequestMapping("/api/admin/members")
@RequiredArgsConstructor
public class AdminMemberController {

    private final AdminMemberService adminMemberService;

    @GetMapping
    public ResponseEntity<MembersResponse> getMembers(
            @PageableDefault Pageable pageable
    ) {
        MembersResponse response = MembersResponse.from(adminMemberService.getMembers(pageable));

        return ResponseEntity.status(HttpStatus.OK)
                .body(response);
    }

    @PostMapping
    public ResponseEntity<Void> registerMember(
            @RequestBody @Validated AdminMemberRegisterRequest request
    ) {
        adminMemberService.registerMember(request.toMemberRegisterDto());

        return ResponseEntity.status(HttpStatus.CREATED)
                .build();
    }
}
