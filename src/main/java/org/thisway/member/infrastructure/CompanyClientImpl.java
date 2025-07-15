package org.thisway.member.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.thisway.company.domain.CompanyService;
import org.thisway.member.domain.CompanyClient;
import org.thisway.member.domain.CompanyInfo;
import org.thisway.member.domain.Member;
import org.thisway.support.common.CustomException;
import org.thisway.support.common.ErrorCode;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CompanyClientImpl implements CompanyClient {

    private final CompanyService companyService;

    @Override
    public Optional<CompanyInfo> findById(long id) {
        return companyService.findById(id)
                .map(this::from);
    }

    @Override
    public CompanyInfo getById(long id) {
        return companyService.findById(id)
                .map(this::from)
                .orElseThrow(() -> new CustomException(ErrorCode.SERVER_ERROR, "회사 데이터를 가져오는데 실패했습니다."));
    }

    @Override
    public List<CompanyInfo> findAllById(Collection<Long> ids) {
        return companyService.findAllById(ids)
                .stream().map(this::from)
                .toList();
    }

    @Override
    public List<CompanyInfo> findAllByMember(Collection<Member> members) {
        List<Long> ids = members.stream()
                .map(Member::getCompanyId)
                .toList();
        return findAllById(ids);
    }

    @Override
    public boolean existById(Long companyId) {
        return companyService.existById(companyId);
    }

    private CompanyInfo from(org.thisway.company.domain.Company company) {
        return CompanyInfo.builder()
                .id(company.getId())
                .name(company.getName())
                .crn(company.getCrn())
                .contact(company.getContact())
                .addrRoad(company.getAddrRoad())
                .addrDetail(company.getAddrDetail())
                .memo(company.getMemo())
                .gpsCycle(company.getGpsCycle())
                .build();
    }
}
