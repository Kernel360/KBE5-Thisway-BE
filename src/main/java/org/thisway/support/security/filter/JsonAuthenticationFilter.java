package org.thisway.support.security.filter;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.thisway.support.security.dto.request.LoginRequest;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class JsonAuthenticationFilter extends AbstractAuthenticationProcessingFilter {

    private static final AntPathRequestMatcher DEFAULT_ANT_PATH_REQUEST_MATCHER = new AntPathRequestMatcher(
            "/api/auth/login", "POST");

    private final ObjectMapper objectMapper = new ObjectMapper();

    public JsonAuthenticationFilter(AuthenticationManager authManager) {
        super(DEFAULT_ANT_PATH_REQUEST_MATCHER);
        setAuthenticationManager(authManager);
    }

    @Override
    public Authentication attemptAuthentication(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws AuthenticationException, IOException {

        String contentType = request.getContentType();

        if (contentType == null
                || !contentType.toLowerCase()
                .startsWith(MediaType.APPLICATION_JSON_VALUE))
            throw new AuthenticationServiceException("Unsupported content type: " + request.getContentType());

        LoginRequest loginRequest = objectMapper.readValue(
                request.getInputStream(),
                LoginRequest.class);

        UsernamePasswordAuthenticationToken authRequest = UsernamePasswordAuthenticationToken.unauthenticated(
                loginRequest.email(),
                loginRequest.password());

        return getAuthenticationManager().authenticate(authRequest);
    }
}
