package org.thisway.vehicle.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.thisway.vehicle.entity.VehicleModel;

public interface VehicleModelRepository extends JpaRepository<VehicleModel, Long> {
  boolean existsByManufacturerAndNameAndModelYear(String manufacturer, String name, Integer year);

}
