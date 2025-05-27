package org.thisway.company.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.common.BaseEntity;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;
import org.thisway.company.dto.request.CompanyRegisterRequest;
import org.thisway.company.dto.response.CompaniesResponse;
import org.thisway.company.dto.response.CompanyResponse;
import org.thisway.company.entity.Company;
import org.thisway.company.repository.CompanyRepository;

@Service
@RequiredArgsConstructor
@Transactional
public class CompanyService {

    private final CompanyRepository companyRepository;

    @Transactional(readOnly = true)
    public CompanyResponse getCompanyDetail(Long id) {
        return companyRepository.findById(id)
                .filter(BaseEntity::isActive)
                .map(CompanyResponse::from)
                .orElseThrow(() -> new CustomException(ErrorCode.COMPANY_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public CompaniesResponse getCompanies(Pageable pageable) {
        return CompaniesResponse.from(companyRepository.findAllByActiveTrue(pageable));
    }

    public void registerCompany(CompanyRegisterRequest request) {
        Boolean existingCompany = companyRepository.existsByCrn(request.crn());
        if (existingCompany) {
            throw new CustomException(ErrorCode.COMPANY_ALREADY_EXIST);
        }
        Company company = Company.builder()
                .name(request.name())
                .crn(request.crn())
                .contact(request.contact())
                .addrRoad(request.addrRoad())
                .addrDetail(request.addrDetail())
                .memo(request.memo())
                .gpsCycle(request.gpsCycle())
                .build();

        companyRepository.save(company);
    }

    public void deleteCompany(Long id) {
        companyRepository.findById(id)
                .filter(BaseEntity::isActive)
                .orElseThrow(() -> new CustomException(ErrorCode.COMPANY_NOT_FOUND))
                .delete();
    }
}
