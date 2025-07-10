package org.thisway.vehicle.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.thisway.company.domain.Company;
import org.thisway.vehicle.dto.request.VehicleSearchRequest;
import org.thisway.vehicle.entity.Vehicle;

import java.util.List;

public interface VehicleQueryRepository {

    Page<Vehicle> searchActiveVehicles(Company company, VehicleSearchRequest request, Pageable pageable);

    List<Vehicle> getAllDrivingVehicles(Long companyId);
}
