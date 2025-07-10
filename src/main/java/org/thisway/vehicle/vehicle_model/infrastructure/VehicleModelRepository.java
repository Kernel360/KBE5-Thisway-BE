package org.thisway.vehicle.vehicle_model.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.thisway.vehicle.vehicle_model.domain.VehicleModel;

import java.util.Optional;

public interface VehicleModelRepository extends JpaRepository<VehicleModel, Long> {
    boolean existsByManufacturerAndNameAndModelYear(String manufacturer, String name, Integer year);

    Page<VehicleModel> findAllByActiveTrue(Pageable pageable);

    Optional<VehicleModel> findByIdAndActiveTrue(Long id);

}
