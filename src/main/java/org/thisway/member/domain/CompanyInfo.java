package org.thisway.member.domain;

import lombok.Builder;

@Builder
public record CompanyInfo(
        long id,
        String name,
        String crn,
        String contact,
        String addrRoad,
        String addrDetail,
        String memo,
        Integer gpsCycle
) {
}
