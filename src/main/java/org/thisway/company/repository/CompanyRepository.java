package org.thisway.company.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.thisway.company.entity.Company;

public interface CompanyRepository extends JpaRepository<Company, Long> {
}
