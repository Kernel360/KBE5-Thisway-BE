package org.thisway.vehicle.validation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;
import org.thisway.vehicle.dto.request.VehicleUpdateRequest;
import org.thisway.vehicle.entity.Vehicle;
import org.thisway.vehicle.repository.VehicleRepository;

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
                throw new CustomException(ErrorCode.DUPLICATE_CAR_NUMBER);
            }
        }
    }

    private void validateRequestNotEmpty(VehicleUpdateRequest request) {
        if (hasNoUpdates(request)) {
            throw new CustomException(ErrorCode.EMPTY_UPDATE_REQUEST);
        }
    }

    private boolean hasNoUpdates(VehicleUpdateRequest request) {
        return request.carNumber() == null &&
                request.color() == null &&
                request.manufacturer() == null &&
                request.modelYear() == null &&
                request.model() == null;
    }
}
