package org.thisway.vehicle.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.thisway.company.domain.Company;
import org.thisway.vehicle.domain.Vehicle;

import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, Long>, VehicleQueryRepository {

    Optional<Vehicle> findByIdAndActiveTrue(Long id);

    boolean existsByCarNumberAndActiveTrue(String carNumber);

    Page<Vehicle> findAllByCompanyAndActiveTrue(Company company, Pageable pageable);

    Page<Vehicle> findAllByCompanyIdAndPowerOnIsAndActiveTrue(long companyId, boolean powerOn, Pageable pageable);

    long countByCompanyIdAndActiveTrue(long companyId);

    long countByCompanyIdAndPowerOnIsAndActiveTrue(long companyId, boolean powerOn);
}
