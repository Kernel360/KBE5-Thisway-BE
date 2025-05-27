package org.thisway.auth.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.thisway.auth.dto.request.PasswordChangeRequest;
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

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@RequestBody PasswordChangeRequest request) {
        emailVerificationService.changePassword(request);
        return ApiResponse.ok();
    }
}
