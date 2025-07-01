package org.thisway.vehicle.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.thisway.company.entity.Company;
import org.thisway.vehicle.dto.request.VehicleSearchRequest;
import org.thisway.vehicle.entity.Vehicle;

public interface VehicleQueryRepository {

    Page<Vehicle> searchActiveVehicles(Company company, VehicleSearchRequest request, Pageable pageable);
}
