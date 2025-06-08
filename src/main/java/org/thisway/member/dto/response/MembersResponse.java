package org.thisway.member.dto.response;


import java.util.List;
import org.springframework.data.domain.Page;
import org.thisway.common.PageInfo;
import org.thisway.member.dto.MembersDto;
import org.thisway.member.entity.Member;

public record MembersResponse(
        List<MemberResponse> members,

        PageInfo pageInfo
) {

    public static MembersResponse from(Page<Member> memberPage) {
        List<MemberResponse> members = memberPage.map(MemberResponse::from).toList();
        PageInfo pageInfo = PageInfo.from(memberPage);

        return new MembersResponse(members, pageInfo);
    }

    public static MembersResponse from(MembersDto membersDto) {
        List<MemberResponse> members = membersDto.members().stream().map(MemberResponse::from).toList();
        return new MembersResponse(members, membersDto.pageInfo());
    }
}
