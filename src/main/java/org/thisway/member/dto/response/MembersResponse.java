package org.thisway.member.dto.response;


import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.springframework.data.domain.Page;
import org.thisway.common.PageInfo;
import org.thisway.member.entity.Member;

public record MembersResponse(
        @JsonProperty(value = "members")
        List<MemberResponse> memberResponses,

        PageInfo pageInfo
) {

    public static MembersResponse from(Page<Member> members) {
        List<MemberResponse> memberResponse = members.map(MemberResponse::from).toList();
        PageInfo pageInfo = PageInfo.from(members);

        return new MembersResponse(memberResponse, pageInfo);
    }
}
