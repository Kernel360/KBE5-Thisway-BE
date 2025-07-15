package org.thisway.member.domain;

import lombok.Builder;

public class MemberInfo {

    @Builder
    public record Member(
            long id,
            String companyName,
            MemberRole role,
            String name,
            String email,
            String phone,
            String memo
    ) {
    }

    @Builder
    public record MemberSummary(
            long companyChefCount,
            long companyAdminCount,
            long memberCount
    ) {
    }

    @Builder
    public record MemberWithCompany(
            long id,
            CompanyInfo companyInfo,
            MemberRole role,
            String name,
            String email,
            String phone,
            String memo
    ) {
    }
}
