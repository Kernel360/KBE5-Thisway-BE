package org.thisway.vehicle.util;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.thisway.support.common.CustomException;
import org.thisway.support.common.ErrorCode;
import org.thisway.vehicle.domain.Vehicle;
import org.thisway.vehicle.infrastructure.VehicleRepository;
import org.thisway.vehicle.interfaces.VehicleUpdateRequest;

@Component
@RequiredArgsConstructor
public class VehicleUpdateValidator {

    private final VehicleRepository vehicleRepository;

    public void validateUpdateRequest(Vehicle existingVehicle, VehicleUpdateRequest request) {
        validateCarNumberIfChanged(existingVehicle, request.carNumber());
        validateRequestNotEmpty(request);
    }

    private void validateCarNumberIfChanged(Vehicle vehicle, String newCarNumber) {
        if (newCarNumber != null && !newCarNumber.equals(vehicle.getCarNumber())) {
            if (vehicleRepository.existsByCarNumberAndActiveTrue(newCarNumber)) {
                throw new CustomException(ErrorCode.VEHICLE_DUPLICATE_CAR_NUMBER);
            }
        }
    }

    private void validateRequestNotEmpty(VehicleUpdateRequest request) {
        if (hasNoUpdates(request)) {
            throw new CustomException(ErrorCode.VEHICLE_EMPTY_UPDATE_REQUEST);
        }
    }

    private boolean hasNoUpdates(VehicleUpdateRequest request) {
        return request.vehicleModelId() == null &&
                request.carNumber() == null &&
                request.color() == null;
    }
}

