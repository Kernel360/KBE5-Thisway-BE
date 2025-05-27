package org.thisway.company.repository;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.thisway.company.entity.Company;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findByIdAndActiveTrue(Long id);

    Page<Company> findAllByActiveTrue(Pageable pageable);

    Boolean existsByCrn(String crn);
}
