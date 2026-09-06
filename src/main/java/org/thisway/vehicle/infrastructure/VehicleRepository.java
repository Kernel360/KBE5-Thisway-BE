package org.thisway.vehicle.infrastructure;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.thisway.vehicle.domain.Vehicle;

public interface VehicleRepository extends JpaRepository<Vehicle, Long>, VehicleQueryRepository {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT v FROM Vehicle v WHERE v.id = :id AND v.active = true")
    Optional<Vehicle> lockActiveById(@org.springframework.data.repository.query.Param("id") Long id);

    Optional<Vehicle> findByIdAndActiveTrue(Long id);

    Optional<Vehicle> findByIdAndCompanyIdAndActiveTrue(Long id, Long companyId);

    boolean existsByCarNumberAndActiveTrue(String carNumber);

    Page<Vehicle> findAllByCompanyIdAndPowerOnIsAndActiveTrue(long companyId, boolean powerOn, Pageable pageable);

    long countByCompanyIdAndActiveTrue(long companyId);

    long countByCompanyIdAndPowerOnIsAndActiveTrue(long companyId, boolean powerOn);
}
