package org.thisway.auth.domain;

public interface PasswordEncoderMatch {

    void assertMatches(CharSequence rawPassword, String encodedPassword);

}
