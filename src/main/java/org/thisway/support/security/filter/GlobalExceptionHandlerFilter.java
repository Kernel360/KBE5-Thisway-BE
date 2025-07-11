package org.thisway.support.security.filter;

import java.io.IOException;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class GlobalExceptionHandlerFilter extends OncePerRequestFilter {
    private final ObjectMapper objectMapper;

    // TODO: Custom Filter에서 발생한 예외 처리 하는 부분인데 아직 미완성.
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } catch (JwtException ex) {
            log.error("JWT authentication error", ex);
            sendError(
                    response,
                    HttpStatus.UNAUTHORIZED,
                    "UNAUTHORIZED",
                    ex.getMessage());

        } catch (AuthenticationServiceException ex) {
            log.error("Authentication service error", ex);
            sendError(
                    response,
                    HttpStatus.BAD_REQUEST,
                    "BAD_REQUEST",
                    ex.getMessage());

        } catch (BadCredentialsException ex) {
            log.error("Bad credentials error", ex);
            sendError(
                    response,
                    HttpStatus.UNAUTHORIZED,
                    "UNAUTHORIZED",
                    ex.getMessage());

        } catch (Exception ex) {
            log.error("Unhandled exception occurred", ex);
            sendError(
                    response,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "INTERNAL_SERVER_ERROR",
                    ex.getMessage());

        }
    }

    // TODO:: 이 부분은 공통 응답과 맞출 수 있게 수정해야 함
    private void sendError(HttpServletResponse response,
                           HttpStatus status,
                           String code,
                           String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getWriter(),
                Map.of("code", code, "message", message));
    }

}
