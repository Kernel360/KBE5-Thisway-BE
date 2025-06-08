package org.thisway.member.dto;

import java.util.List;
import org.springframework.data.domain.Page;
import org.thisway.common.PageInfo;
import org.thisway.member.entity.Member;

public record MembersDto(
        List<MemberDto> members,

        PageInfo pageInfo
) {

    public static MembersDto from(Page<Member> memberPage) {
        List<MemberDto> members = memberPage.map(MemberDto::from).toList();
        PageInfo pageInfo = PageInfo.from(memberPage);

        return new MembersDto(members, pageInfo);
    }
}
