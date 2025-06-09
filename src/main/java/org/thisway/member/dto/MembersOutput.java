package org.thisway.member.dto;

import java.util.List;
import org.springframework.data.domain.Page;
import org.thisway.common.PageInfo;
import org.thisway.member.entity.Member;

public record MembersOutput(
        List<MemberOutput> members,

        PageInfo pageInfo
) {

    public static MembersOutput from(Page<Member> memberPage) {
        List<MemberOutput> members = memberPage.map(MemberOutput::from).toList();
        PageInfo pageInfo = PageInfo.from(memberPage);

        return new MembersOutput(members, pageInfo);
    }
}
