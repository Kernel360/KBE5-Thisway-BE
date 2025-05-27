package org.thisway.auth.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.thisway.auth.dto.request.SendVerifyCodeRequest;
import org.thisway.auth.service.EmailVerificationService;
import org.thisway.common.ApiResponse;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final EmailVerificationService emailVerificationService;

    @PostMapping("/verify-code")
    public ApiResponse<Void> sendVerifyCode(@RequestBody SendVerifyCodeRequest request) {
        emailVerificationService.sendVerifyCode(request.email());
        return ApiResponse.ok();
    }
}
