package org.thisway.company.intrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.thisway.company.domain.Company;

import java.util.List;
import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findByIdAndActiveTrue(Long id);

    Page<Company> findAllByActiveTrue(Pageable pageable);

    Boolean existsByCrn(String crn);

    @Query("SELECT c.id FROM Company c WHERE c.active = true")
    List<Long> findAllActiveCompanyIds();
}
