package org.thisway.auth.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.thisway.auth.dto.request.SendVerifyCodeRequest;
import org.thisway.auth.service.EmailVerificationService;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final EmailVerificationService emailVerificationService;

    @PostMapping("/verify-code")
    public ResponseEntity<Void> sendVerifyCode(@RequestBody SendVerifyCodeRequest request) {
        emailVerificationService.sendVerifyCode(request.email());
        return ResponseEntity.status(HttpStatus.OK)
                .build();
    }
}
