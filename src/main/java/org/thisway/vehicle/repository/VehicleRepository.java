package org.thisway.vehicle.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.thisway.company.entity.Company;
import org.thisway.vehicle.entity.Vehicle;

import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    Optional<Vehicle> findByIdAndActiveTrue(Long id);

    boolean existsByCarNumberAndActiveTrue(String carNumber);

    Page<Vehicle> findAllByCompanyAndActiveTrue(Company company, Pageable pageable);

    long countByCompanyIdAndActiveTrue(long companyId);

    long countByCompanyIdAndPowerOnIsAndActiveTrue(long companyId, boolean powerOn);
}
