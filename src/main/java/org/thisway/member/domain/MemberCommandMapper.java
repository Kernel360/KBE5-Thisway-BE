package org.thisway.member.domain;

import org.springframework.stereotype.Component;

@Component
public class MemberCommandMapper {

    public Member from(MemberCommand.RegisterMember command) {
        return Member.builder()
                .companyId(command.getCompanyId())
                .role(command.getRole())
                .name(command.getName())
                .email(command.getEmail())
                .password(command.getPassword())
                .phone(command.getPhone())
                .memo(command.getMemo())
                .build();
    }
}
