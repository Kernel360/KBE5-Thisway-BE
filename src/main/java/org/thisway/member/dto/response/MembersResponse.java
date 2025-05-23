package org.thisway.member.dto.response;


import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.domain.Page;
import org.thisway.member.entity.Member;

public record MembersResponse(
        @JsonProperty(value = "members")
        Page<MemberResponse> memberResponses
) {

    public static MembersResponse from(Page<Member> members) {
        return new MembersResponse(members.map(MemberResponse::from));
    }
}
