package org.thisway.vehicle.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.thisway.vehicle.entity.Vehicle;

import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    Optional<Vehicle> findByIdAndActiveTrue(Long id);

    Page<Vehicle> findAllByActiveTrue(Pageable pageable);
}
