package org.thisway.company.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.common.BaseEntity;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;
import org.thisway.company.dto.AdminCompaniesOutput;
import org.thisway.company.dto.AdminCompanyDetailOutput;
import org.thisway.company.dto.AdminCompanyRegisterInput;
import org.thisway.company.dto.AdminCompanyUpdateInput;
import org.thisway.company.entity.Company;
import org.thisway.company.repository.CompanyRepository;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminCompanyService {

    private final CompanyRepository companyRepository;

    @Transactional(readOnly = true)
    public AdminCompanyDetailOutput getCompanyDetail(Long id) {
        return companyRepository.findById(id)
                .filter(BaseEntity::isActive)
                .map(AdminCompanyDetailOutput::from)
                .orElseThrow(() -> new CustomException(ErrorCode.COMPANY_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public AdminCompaniesOutput getCompanies(Pageable pageable) {
        return AdminCompaniesOutput.from(companyRepository.findAllByActiveTrue(pageable));
    }

    public void registerCompany(AdminCompanyRegisterInput request) {
        Boolean existingCompany = companyRepository.existsByCrn(request.crn());
        if (existingCompany) {
            throw new CustomException(ErrorCode.COMPANY_ALREADY_EXIST);
        }

        companyRepository.save(request.toCompany());
    }

    public void updateCompany(AdminCompanyUpdateInput request) {
        Company company = companyRepository.findByIdAndActiveTrue(request.id())
                .orElseThrow(() -> new CustomException(ErrorCode.COMPANY_NOT_FOUND));

        company.updateName(request.name());
        company.updateCrn(request.crn());
        company.updateContact(request.contact());
        company.updateAddrRoad(request.addrRoad());
        company.updateAddrDetail(request.addrDetail());
        company.updateMemo(request.memo());
    }

    public void deleteCompany(Long id) {
        companyRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new CustomException(ErrorCode.COMPANY_NOT_FOUND))
                .delete();
    }
}
