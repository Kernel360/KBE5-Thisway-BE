package org.thisway.vehicle.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.thisway.vehicle.entity.Vehicle;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
}
