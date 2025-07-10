package org.thisway.member.application;

import org.springframework.data.domain.Page;
import org.thisway.common.PageInfo;
import org.thisway.member.domain.Member;

import java.util.List;

public record CompanyChefMembersOutput(
        List<CompanyChefMemberDetailOutput> members,

        PageInfo pageInfo
) {

    public static CompanyChefMembersOutput from(Page<Member> memberPage) {
        List<CompanyChefMemberDetailOutput> members = memberPage.map(CompanyChefMemberDetailOutput::from).toList();
        PageInfo pageInfo = PageInfo.from(memberPage);

        return new CompanyChefMembersOutput(members, pageInfo);
    }
}
