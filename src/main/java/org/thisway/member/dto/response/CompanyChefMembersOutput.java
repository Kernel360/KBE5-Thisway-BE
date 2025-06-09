package org.thisway.member.dto.response;

import java.util.List;
import org.springframework.data.domain.Page;
import org.thisway.common.PageInfo;
import org.thisway.member.dto.CompanyChefMemberDetailOutput;
import org.thisway.member.entity.Member;

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
