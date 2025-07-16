package org.thisway.member.domain;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class MemberInfoMapper {

    public MemberInfo.MemberWithCompany toMemberInfo(Member member, CompanyInfo companyInfo) {
        return MemberInfo.MemberWithCompany.builder()
                .id(member.getId())
                .companyInfo(companyInfo)
                .role(member.getRole())
                .name(member.getName())
                .email(member.getEmail())
                .phone(member.getPhoneValue())
                .memo(member.getMemo())
                .build();
    }

    public Page<MemberInfo.MemberWithCompany> toMemberInfos(Page<Member> members, List<CompanyInfo> companies) {
        Map<Long, CompanyInfo> companyInfoMap = companies.stream()
                .collect(Collectors.toMap(CompanyInfo::id, companyInfo -> companyInfo));

        return members.map(member -> {
            CompanyInfo companyInfo = companyInfoMap.get(member.getCompanyId());
            if (companyInfo == null) {
                throw new IllegalArgumentException(
                        "회사와 멤버의 조합을 실패했습니다: Member ID: %d, Company ID: %d".formatted(
                                member.getId(), member.getCompanyId()
                        )
                );
            }
            return toMemberInfo(member, companyInfo);
        });
    }
}
