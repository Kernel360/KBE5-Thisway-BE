package org.thisway.member.entity;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum MemberRole {

    ADMIN(4),
    COMPANY_CHEF(3),
    COMPANY_ADMIN(2),
    MEMBER(1),
    ;

    private final int level;

    public Set<MemberRole> getLowerOrEqualRoles() {
        return Arrays.stream(MemberRole.values())
                .filter(role -> role.level <= this.level)
                .collect(Collectors.toSet());
    }
}
