package org.thisway.auth.application;

import org.springframework.stereotype.Service;
import org.thisway.auth.domain.AuthToken;
import org.thisway.auth.domain.PasswordEncoderMatch;
import org.thisway.auth.domain.TokenGenerator;
import org.thisway.member.domain.Member;
import org.thisway.member.domain.MemberReader;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final MemberReader memberReader;
    private final PasswordEncoderMatch passwordEncoderMatch;
    private final TokenGenerator tokenGenerator;

    @Override
    public AuthInfo.LoginResult login(AuthCommand.LoginRequest request) {

        Member member = memberReader.requireActiveMemberByEmail(request.getEmail());
        passwordEncoderMatch.assertMatches(request.getPassword(), member.getPassword());
        AuthToken authToken = tokenGenerator.generator(member);
        return AuthInfo.LoginResult.from(authToken);
    }
}
