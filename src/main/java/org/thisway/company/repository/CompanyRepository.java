package org.thisway.company.repository;

import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.thisway.company.entity.Company;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    Page<Company> findAllByActiveTrue(Pageable pageable);

    Boolean existsByCrn(@NotBlank String crn);
}
