package org.thisway.vehicle.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.thisway.company.domain.Company;
import org.thisway.vehicle.interfaces.VehicleSearchRequest;
import org.thisway.vehicle.domain.Vehicle;

import java.util.List;

public interface VehicleQueryRepository {

    Page<Vehicle> searchActiveVehicles(Company company, VehicleSearchRequest request, Pageable pageable);

    List<Vehicle> getAllDrivingVehicles(Long companyId);
}
