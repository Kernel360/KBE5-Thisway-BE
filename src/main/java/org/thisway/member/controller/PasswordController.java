package org.thisway.member.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.thisway.member.dto.request.PasswordChangeRequest;
import org.thisway.member.dto.request.SendVerificationCodeRequest;
import org.thisway.member.service.PasswordService;

@RestController
// TODO : 엔드포인트 논의 필요
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class PasswordController {

    private final PasswordService passwordService;

    @PostMapping("/verify-code")
    public ResponseEntity<Void> sendVerifyCode(@RequestBody SendVerificationCodeRequest request) {
        passwordService.sendVerificationCode(request.email());
        return ResponseEntity.status(HttpStatus.OK)
                .build();
    }

    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(@RequestBody PasswordChangeRequest request) {
        passwordService.changePassword(request.email(), request.code(), request.newPassword());
        return ResponseEntity.status(HttpStatus.OK)
                .build();
    }
}
