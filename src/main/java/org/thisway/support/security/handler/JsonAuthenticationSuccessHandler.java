package org.thisway.support.security.handler;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.thisway.support.security.dto.request.MemberDetails;
import org.thisway.support.security.utils.JwtTokenUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

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
            Authentication authentication
    ) throws IOException, ServletException {
        String subject = authentication.getName();
        Map<String, Object> claims = new HashMap<>();

        claims.put("roles",
                authentication.getAuthorities()
                        .stream()
                        .map(GrantedAuthority::getAuthority)
                        .toList()
        );

        if (authentication.getPrincipal() instanceof MemberDetails memberDetails) {
            claims.put("companyId", memberDetails.getCompanyId());
        } else {
            log.error(
                    "Authentication principal is not instance of MemberDetails: {}",
                    authentication.getPrincipal().getClass().getName()
            );
        }

        String jwt = jwtTokenUtil.createAccessToken(subject, claims);

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        Map<String, String> payload = new HashMap<>();
        payload.put("token", jwt);

        ObjectMapper objectMapper = new ObjectMapper();
        String json = objectMapper.writeValueAsString(payload);
        response.getWriter().write(json);
        response.getWriter().flush();
    }

}
