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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.thisway.member.dto.request.MemberRegisterRequest;
import org.thisway.member.dto.response.MemberResponse;
import org.thisway.member.dto.response.MemberSummaryResponse;
import org.thisway.member.dto.response.MembersResponse;
import org.thisway.member.service.MemberService;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @GetMapping("/{id}")
    public ResponseEntity<MemberResponse> getMemberDetail(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(memberService.getMemberDetail(id));
    }

    // todo: 업체 최고 담당자가 조회할 경우 특정 업체 Member만 조회 가능하도록 변경 (인증 기능 추가 후)
    @GetMapping
    public ResponseEntity<MembersResponse> getMembers(@PageableDefault Pageable pageable) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(memberService.getMembers(pageable));
    }

    @PostMapping
    public ResponseEntity<Void> registerMember(@RequestBody @Validated MemberRegisterRequest request) {
        memberService.registerMember(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMember(@PathVariable Long id) {
        memberService.deleteMember(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT)
                .build();
    }

    @GetMapping("/summary")
    public ResponseEntity<MemberSummaryResponse> summary() {
        return ResponseEntity.status(HttpStatus.OK)
                .body(MemberSummaryResponse.from(memberService.summary()));
    }
}
