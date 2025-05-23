package org.thisway.member.support;

import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.thisway.member.dto.request.MemberRegisterRequest;
import org.thisway.member.dto.response.MemberResponse;
import org.thisway.member.dto.response.MembersResponse;
import org.thisway.member.entity.Member;

public class MemberFixture {

    public static MemberRegisterRequest createMemberRegisterRequest() {
        return new MemberRegisterRequest(
                "홍길동",
                "hong@example.com",
                "Password123!",
                "010-1234-5678",
                "가입 메모"
        );
    }

    public static Member createMember() {
        return Member.builder()
                .name("홍길동")
                .email("hong@example.com")
                .password("Password123!")
                .phone("010-1234-5678")
                .memo("가입 메모")
                .build();
    }

    public static MemberResponse createMemberResponse(long id) {
        return new MemberResponse(
                1L,
                "홍길동",
                "hong@example.com",
                "010-1234-5678",
                "가입 메모"
        );
    }

    public static MembersResponse createMembersResponse(int size) {
        List<Member> members = new ArrayList<>();
        for (int i = 1; i <= size; i++) {
            members.add(createMember());
        }

        Page<Member> page = new PageImpl<>(
                members,
                PageRequest.of(0, size),
                size
        );

        return MembersResponse.from(page);
    }
}
