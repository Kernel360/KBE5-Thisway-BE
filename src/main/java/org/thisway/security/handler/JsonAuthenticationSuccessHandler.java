package org.thisway.security.handler;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.thisway.security.utils.JwtTokenUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JsonAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenUtil jwtTokenUtil;

    public JsonAuthenticationSuccessHandler(JwtTokenUtil jwtTokenUtil) {
        this.jwtTokenUtil = jwtTokenUtil;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        // Handle successful authentication
        String subject = authentication.getName();
        Map<String, Object> claims = new HashMap<>();

        authentication
                .getAuthorities()
                .forEach(auth -> claims.put(
                        auth.getAuthority(),
                        true));

        // log.info("Authentication successful for user: {} {}", subject,
        // authentication.getAuthorities());
        log.info("creating JWT for user: {} with claims: {}", subject, claims);

        String jwt = jwtTokenUtil.createAccessToken(subject, claims);

        response.setStatus(HttpServletResponse.SC_OK);
        response.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + jwt);
    }

}
