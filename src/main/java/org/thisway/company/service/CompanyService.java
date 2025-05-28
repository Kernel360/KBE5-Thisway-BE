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

        companyRepository.save(request.toCompany());
    }

    public void deleteCompany(Long id) {
        companyRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new CustomException(ErrorCode.COMPANY_NOT_FOUND))
                .delete();
    }
}
