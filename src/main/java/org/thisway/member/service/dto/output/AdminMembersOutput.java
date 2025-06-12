package org.thisway.member.service.dto.output;

import java.util.List;
import org.springframework.data.domain.Page;
import org.thisway.common.PageInfo;
import org.thisway.member.entity.Member;

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
