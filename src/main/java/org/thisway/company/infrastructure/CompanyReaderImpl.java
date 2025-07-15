package org.thisway.company.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.thisway.company.domain.Company;
import org.thisway.company.domain.CompanyReader;
import org.thisway.support.common.BaseEntity;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CompanyReaderImpl implements CompanyReader {

    private final CompanyRepository companyRepository;

    @Override
    public Optional<Company> findById(long id) {
        return companyRepository.findById(id)
                .filter(BaseEntity::isActive);
    }

    @Override
    public List<Company> findAllById(Iterable<Long> ids) {
        return companyRepository.findAllById(ids);
    }

    @Override
    public boolean existById(long companyId) {
        return companyRepository.existsById(companyId);
    }
}
