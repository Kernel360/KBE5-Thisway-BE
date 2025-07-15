package org.thisway.member.interfaces.companychef;

import org.springframework.stereotype.Component;
import org.thisway.member.domain.MemberCommand;
import org.thisway.member.domain.MemberQuery;

@Component
public class CompanyChefMemberRequestMapper {

    public MemberCommand.RegisterMember from(CompanyChefMemberRequest.MemberRegisterRequest dto, long companyId) {
        return MemberCommand.RegisterMember.builder()
                .companyId(companyId)
                .role(dto.role())
                .name(dto.name())
                .email(dto.email())
                .password(dto.password())
                .phone(dto.phone())
                .memo(dto.memo())
                .build();
    }

    public MemberCommand.UpdateMember from(long id, CompanyChefMemberRequest.MemberUpdateRequest dto) {
        return MemberCommand.UpdateMember.builder()
                .id(id)
                .name(dto.name())
                .email(dto.email())
                .phone(dto.phone())
                .memo(dto.memo())
                .build();
    }

    public MemberQuery.SearchMember from(CompanyChefMemberRequest.MemberSearchRequest dto, long companyId) {
        return MemberQuery.SearchMember.builder()
                .name(dto.memberName())
                .companyId(companyId)
                .build();
    }
}
