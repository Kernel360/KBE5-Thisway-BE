package org.thisway.company.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CompanyServiceImpl implements CompanyService {

    private final CompanyReader companyReader;

    @Override
    public Optional<Company> findById(long id) {
        return companyReader.findById(id);
    }

    @Override
    public List<Company> findAllById(Iterable<Long> ids) {
        return companyReader.findAllById(ids);
    }

    @Override
    public boolean existById(long companyId) {
        return companyReader.existById(companyId);
    }
}
