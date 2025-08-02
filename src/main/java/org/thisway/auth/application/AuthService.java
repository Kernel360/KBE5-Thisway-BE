package org.thisway.auth.application;

public interface AuthService {

    public AuthInfo.LoginResult login(AuthCommand.LoginRequest request);

}
