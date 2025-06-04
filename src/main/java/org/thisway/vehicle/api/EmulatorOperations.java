package org.thisway.vehicle.api;

public interface EmulatorOperations {
    void startEmulator(Long vehicleId, String mdn);

    void stopEmulator(Long vehicleId, String mdn);
}
