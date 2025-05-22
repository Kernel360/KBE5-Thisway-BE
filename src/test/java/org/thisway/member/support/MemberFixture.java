package org.thisway.member.support;

import org.thisway.member.dto.request.MemberRegisterRequest;

public class MemberFixture {

    public static MemberRegisterRequest createMemberRegisterRequest() {
        return new MemberRegisterRequest(
                "홍길동",
                "hong@example.com",
                "Password123!",
                "010-1234-5678",
                "가입 메모"
        );
    }
}
