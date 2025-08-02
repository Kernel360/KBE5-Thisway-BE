package org.thisway.auth.interfaces;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.thisway.auth.application.AuthInfo;
import org.thisway.auth.application.AuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<AuthDto.LoginResponse> login(
            @Valid
            @RequestBody
            AuthDto.LoginRequest request
    ) {
        AuthInfo.LoginResult authInfo = authService.login(request.toCommand());
        var response = AuthDto.LoginResponse.from(authInfo);

        return ResponseEntity.status(HttpStatus.OK)
                .body(response);
    }
}
