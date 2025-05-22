package org.thisway.member.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.thisway.common.ApiResponse;
import org.thisway.member.dto.request.MemberRegisterRequest;
import org.thisway.member.service.MemberService;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @PostMapping
    public ApiResponse<Void> registerMember(@RequestBody @Validated MemberRegisterRequest request) {
        memberService.registerMember(request);

        return ApiResponse.created();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteMember(@PathVariable Long id) {
        memberService.deleteMember(id);

        return ApiResponse.noContent();
    }
}
