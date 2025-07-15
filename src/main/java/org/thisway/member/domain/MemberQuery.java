package org.thisway.member.domain;

import lombok.Builder;

import java.util.Collection;

public class MemberQuery {

    @Builder(toBuilder = true)
    public record SearchMember(
            Long companyId,
            Collection<MemberRole> roles,
            String name
    ) {
    }
}
