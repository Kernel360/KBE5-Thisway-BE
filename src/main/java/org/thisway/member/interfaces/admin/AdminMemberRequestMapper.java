package org.thisway.member.interfaces.admin;

import org.springframework.stereotype.Component;
import org.thisway.member.domain.MemberCommand;

@Component
public class AdminMemberRequestMapper {

    public MemberCommand.RegisterMember from(AdminMemberRequest.MemberRegisterRequest dto) {
        return MemberCommand.RegisterMember.builder()
                .companyId(dto.companyId())
                .role(dto.role())
                .name(dto.name())
                .email(dto.email())
                .password(dto.password())
                .phone(dto.phone())
                .memo(dto.memo())
                .build();
    }

    public MemberCommand.UpdateMember from(AdminMemberRequest.MemberUpdateRequest dto, long id) {
        return MemberCommand.UpdateMember.builder()
                .id(id)
                .name(dto.name())
                .email(dto.email())
                .phone(dto.phone())
                .memo(dto.memo())
                .build();
    }
}
