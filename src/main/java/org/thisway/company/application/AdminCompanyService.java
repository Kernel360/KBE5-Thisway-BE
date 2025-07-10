package org.thisway.company.application;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.support.common.BaseEntity;
import org.thisway.support.common.CustomException;
import org.thisway.support.common.ErrorCode;
import org.thisway.company.domain.Company;
import org.thisway.company.intrastructure.CompanyRepository;

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
