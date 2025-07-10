package org.thisway.member.application;

import org.springframework.data.domain.Page;
import org.thisway.support.common.PageInfo;
import org.thisway.member.domain.Member;

import java.util.List;

public record AdminMembersOutput(
        List<AdminMemberDetailOutput> members,

        PageInfo pageInfo
) {

    public static AdminMembersOutput from(Page<Member> memberPage) {
        List<AdminMemberDetailOutput> members = memberPage.map(AdminMemberDetailOutput::from).toList();
        PageInfo pageInfo = PageInfo.from(memberPage);

        return new AdminMembersOutput(members, pageInfo);
    }
}
