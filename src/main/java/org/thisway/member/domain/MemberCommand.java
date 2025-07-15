package org.thisway.member.domain;

import lombok.Builder;
import lombok.Value;

public class MemberCommand {

    @Value
    @Builder
    public static class RegisterMember {
        long companyId;
        MemberRole role;
        String name;
        String email;
        String password;
        String phone;
        String memo;
    }

    @Value
    @Builder
    public static class UpdateMember {
        long id;
        String name;
        String email;
        String phone;
        String memo;
    }
}
