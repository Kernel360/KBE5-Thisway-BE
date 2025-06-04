package org.thisway.vehicle.api;

import java.util.Optional;
import org.thisway.emulator.entity.Emulator;

public interface EmulatorFinder {
    Optional<Emulator> findByMdn(String mdn);

    Optional<Emulator> findByVehicleId(Long vehicleId);
}
