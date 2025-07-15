package org.thisway.company.domain;

import java.util.List;
import java.util.Optional;

public interface CompanyReader {

    Optional<Company> findById(long id);

    List<Company> findAllById(Iterable<Long> ids);

    boolean existById(long companyId);
}
