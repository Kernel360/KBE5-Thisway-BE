package org.thisway.vehicle.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.thisway.vehicle.entity.VehicleDetail;

public interface VehicleDetailRepository extends JpaRepository<VehicleDetail, Long> {
}
